package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.ServerApplication;
import com.fowoco.server.reliability.application.OutboxClaimService;
import com.fowoco.server.reliability.application.OutboxCompletionTransaction;
import com.fowoco.server.reliability.application.OutboxFailureTransaction;
import com.fowoco.server.reliability.application.OutboxHandlerTransaction;
import com.fowoco.server.reliability.application.OutboxReadService;
import com.fowoco.server.reliability.application.RetryableEventHandlingException;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.application.port.OutboxBacklogReader;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventPublication;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSqlTenantDatabaseContextTest {

    private static final UUID COMPANY_A =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID OUTBOX_EVENT_A =
            UUID.fromString("a8000000-0000-0000-0000-000000000001");
    private static final UUID OUTBOX_EVENT_B =
            UUID.fromString("b8000000-0000-0000-0000-000000000002");
    private static final String[] TENANT_TABLES = {
            "company",
            "user_account",
            "refresh_token",
            "worker",
            "worker_document",
            "stored_file",
            "task",
            "task_checklist_item",
            "task_transition_history",
            "approval_request",
            "external_submission",
            "task_evidence",
            "audit_event",
            "event_publication",
            "event_consumption",
            "document_request_draft",
            "document_request_draft_type"
    };
    private static final String TENANT_TABLE_SQL =
            "public." + String.join(", public.", TENANT_TABLES);

    private String migrationUrl;
    private String migrationUsername;
    private String migrationPassword;
    private String runtimeRole;
    private String runtimePassword;
    private ConfigurableApplicationContext applicationContext;
    private DriverManagerDataSource migrationDataSource;
    private JdbcTemplate migrationJdbc;
    private JdbcTemplate runtimeJdbc;
    private EntityManager entityManager;
    private PlatformTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;
    private TenantDatabaseContext tenantDatabaseContext;

    @BeforeAll
    void setUpRestrictedRuntimeConnection() throws SQLException {
        migrationUrl = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        migrationUsername = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        migrationPassword = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");

        Flyway.configure()
                .dataSource(migrationUrl, migrationUsername, migrationPassword)
                .locations(
                        "classpath:db/migration",
                        "classpath:db/migration-postgresql"
                )
                .load()
                .migrate();

        runtimeRole = "rls_runtime_test_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        runtimePassword = "Rls-test-" + UUID.randomUUID();

        try (Connection connection = migrationConnection();
             Statement statement = connection.createStatement()) {
            String quotedRole = quoteIdentifier(runtimeRole);
            statement.execute("""
                    CREATE ROLE %s
                    LOGIN
                    PASSWORD %s
                    NOSUPERUSER
                    NOCREATEDB
                    NOCREATEROLE
                    NOINHERIT
                    NOREPLICATION
                    NOBYPASSRLS
                    """.formatted(quotedRole, quoteLiteral(runtimePassword)));
            statement.execute(
                    "GRANT CONNECT ON DATABASE "
                            + quoteIdentifier(connection.getCatalog())
                            + " TO "
                            + quotedRole
            );
            statement.execute("GRANT USAGE ON SCHEMA public TO " + quotedRole);
            statement.execute("""
                    GRANT SELECT, INSERT, UPDATE, DELETE
                    ON TABLE %s
                    TO %s
                    """.formatted(TENANT_TABLE_SQL, quotedRole));
        }

        migrationDataSource = new DriverManagerDataSource();
        migrationDataSource.setUrl(migrationUrl);
        migrationDataSource.setUsername(migrationUsername);
        migrationDataSource.setPassword(migrationPassword);
        migrationJdbc = new JdbcTemplate(migrationDataSource);

        applicationContext = startRestrictedRuntimeApplication();
        DataSource runtimeDataSource = applicationContext.getBean(DataSource.class);
        runtimeJdbc = new JdbcTemplate(runtimeDataSource);
        entityManager = applicationContext.getBean(EntityManager.class);
        transactionManager = applicationContext.getBean(PlatformTransactionManager.class);
        transactionTemplate = new TransactionTemplate(transactionManager);
        tenantDatabaseContext = applicationContext.getBean(TenantDatabaseContext.class);
    }

    @AfterAll
    void removeRestrictedRuntimeConnection() throws SQLException {
        if (applicationContext != null) {
            applicationContext.close();
        }
        if (runtimeRole == null) {
            return;
        }

        try (Connection connection = migrationConnection();
             Statement statement = connection.createStatement()) {
            if (roleExists(statement, runtimeRole)) {
                String quotedRole = quoteIdentifier(runtimeRole);
                statement.execute("DROP OWNED BY " + quotedRole);
                statement.execute("DROP ROLE " + quotedRole);
            }
        }
    }

    @Test
    void runtimeRoleCannotBypassRlsOrModifyPersistentSchema() {
        RoleAttributes attributes = migrationJdbc.queryForObject(
                """
                SELECT
                    rolsuper,
                    rolcreatedb,
                    rolcreaterole,
                    rolinherit,
                    rolreplication,
                    rolbypassrls
                FROM pg_catalog.pg_roles
                WHERE rolname = ?
                """,
                (resultSet, rowNumber) -> new RoleAttributes(
                        resultSet.getBoolean("rolsuper"),
                        resultSet.getBoolean("rolcreatedb"),
                        resultSet.getBoolean("rolcreaterole"),
                        resultSet.getBoolean("rolinherit"),
                        resultSet.getBoolean("rolreplication"),
                        resultSet.getBoolean("rolbypassrls")
                ),
                runtimeRole
        );

        assertThat(attributes).isEqualTo(
                new RoleAttributes(false, false, false, false, false, false)
        );
        assertThat(migrationJdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_catalog.pg_auth_members membership
                JOIN pg_catalog.pg_roles member_role
                  ON member_role.oid = membership.member
                WHERE member_role.rolname = ?
                """,
                Integer.class,
                runtimeRole
        )).isZero();
        assertThat(migrationJdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_catalog.pg_tables
                WHERE schemaname = 'public'
                  AND tableowner = ?
                """,
                Integer.class,
                runtimeRole
        )).isZero();

        assertThat(runtimeJdbc.queryForObject(
                "SELECT CURRENT_USER",
                String.class
        )).isEqualTo(runtimeRole);
        assertThat(runtimeJdbc.queryForObject(
                """
                SELECT pg_catalog.has_schema_privilege(
                    CURRENT_USER, 'public', 'CREATE'
                )
                """,
                Boolean.class
        )).isFalse();
        assertThat(runtimeJdbc.queryForObject(
                """
                SELECT pg_catalog.has_database_privilege(
                    CURRENT_USER, pg_catalog.current_database(), 'CREATE'
                )
                """,
                Boolean.class
        )).isFalse();

        for (String table : TENANT_TABLES) {
            assertThat(hasTablePrivilege(table, "SELECT")).isTrue();
            assertThat(hasTablePrivilege(table, "INSERT")).isTrue();
            assertThat(hasTablePrivilege(table, "UPDATE")).isTrue();
            assertThat(hasTablePrivilege(table, "DELETE")).isTrue();
            assertThat(hasTablePrivilege(table, "TRUNCATE")).isFalse();
            assertThat(hasTablePrivilege(table, "REFERENCES")).isFalse();
        }
        assertThat(hasTablePrivilege("flyway_schema_history", "SELECT")).isFalse();
        assertThat(hasTablePrivilege("flyway_schema_history", "INSERT")).isFalse();
        assertThat(hasTablePrivilege("flyway_schema_history", "UPDATE")).isFalse();
        assertThat(hasTablePrivilege("flyway_schema_history", "DELETE")).isFalse();
        assertThat(hasFunctionPrivilege(
                "bootstrap_company_id_by_normalized_email(text)",
                "EXECUTE"
        )).isFalse();
        assertThat(hasFunctionPrivilege(
                "bootstrap_company_id_by_refresh_token_hash(text)",
                "EXECUTE"
        )).isFalse();
        assertThat(hasFunctionPrivilege(
                "bootstrap_claim_event_publications("
                        + "text,bigint,integer,integer"
                        + ")",
                "EXECUTE"
        )).isFalse();
        assertThat(hasFunctionPrivilege(
                "bootstrap_count_outstanding_event_publications()",
                "EXECUTE"
        )).isFalse();
        assertThat(hasFunctionPrivilege(
                "bootstrap_oldest_outstanding_event_occurred_at()",
                "EXECUTE"
        )).isFalse();
    }

    @Test
    void committedTransactionsReuseAConnectionWithoutLeakingTenantContext() {
        assertThatThrownBy(
                () -> tenantDatabaseContext.setCompanyIdForCurrentTransaction(COMPANY_A)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");

        ContextProbe companyA = bindAndRead(COMPANY_A);
        ContextProbe cleared = readWithoutBinding();
        ContextProbe companyB = bindAndRead(COMPANY_B);

        assertThat(companyA.companyId()).isEqualTo(COMPANY_A.toString());
        assertThat(cleared.companyId()).isNull();
        assertThat(companyB.companyId()).isEqualTo(COMPANY_B.toString());
        assertThat(cleared.backendPid()).isEqualTo(companyA.backendPid());
        assertThat(companyB.backendPid()).isEqualTo(companyA.backendPid());
    }

    @Test
    void rollbackExceptionAndTimeoutDoNotLeakTenantContext() {
        transactionTemplate.executeWithoutResult(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(COMPANY_A);
            assertThat(currentCompanyId()).isEqualTo(COMPANY_A.toString());
            status.setRollbackOnly();
        });
        assertThat(readWithoutBinding().companyId()).isNull();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(COMPANY_A);
            throw new ExpectedTransactionFailure();
        })).isInstanceOf(ExpectedTransactionFailure.class);
        assertThat(readWithoutBinding().companyId()).isNull();

        TransactionTemplate timedTransaction = new TransactionTemplate(transactionManager);
        timedTransaction.setTimeout(1);
        assertThatThrownBy(() -> timedTransaction.executeWithoutResult(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(COMPANY_A);
            runtimeJdbc.execute("SELECT pg_catalog.pg_sleep(2)");
        })).isInstanceOf(DataAccessException.class);
        assertThat(readWithoutBinding().companyId()).isNull();
    }

    @Test
    void transactionCannotBeReboundToAnotherCompany() {
        transactionTemplate.executeWithoutResult(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(COMPANY_A);

            assertThatThrownBy(
                    () -> tenantDatabaseContext.setCompanyIdForCurrentTransaction(COMPANY_B)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot change");
            assertThat(currentCompanyId()).isEqualTo(COMPANY_A.toString());
        });
    }

    @Test
    void transactionOnAnotherDataSourceDoesNotSatisfyTheContextRequirement() {
        TransactionTemplate unrelatedTransaction = new TransactionTemplate(
                new DataSourceTransactionManager(migrationDataSource)
        );

        unrelatedTransaction.executeWithoutResult(status -> assertThatThrownBy(
                () -> tenantDatabaseContext.setCompanyIdForCurrentTransaction(COMPANY_A)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction-bound database connection"));
    }

    @Test
    void springJpaTransactionUsesTheRestrictedRuntimeConnection() {
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);

        ContextProbe contextProbe = bindAndRead(COMPANY_A);

        assertThat(contextProbe.companyId()).isEqualTo(COMPANY_A.toString());
    }

    @Test
    void outboxLifecycleUsesDatabaseClockAndScopedReadsRejectMismatches() {
        String function = "public.bootstrap_claim_event_publications("
                + "text,bigint,integer,integer"
                + ")";
        String countFunction =
                "public.bootstrap_count_outstanding_event_publications()";
        String oldestFunction =
                "public.bootstrap_oldest_outstanding_event_occurred_at()";
        String quotedRole = quoteIdentifier(runtimeRole);
        migrationJdbc.execute("GRANT EXECUTE ON FUNCTION " + function + " TO " + quotedRole);
        migrationJdbc.execute(
                "GRANT EXECUTE ON FUNCTION " + countFunction + " TO " + quotedRole
        );
        migrationJdbc.execute(
                "GRANT EXECUTE ON FUNCTION " + oldestFunction + " TO " + quotedRole
        );
        try {
            insertOutboxProbe(COMPANY_A, OUTBOX_EVENT_A, "{}");
            insertOutboxProbe(COMPANY_B, OUTBOX_EVENT_B, "{}");
            assertInvalidOutboxClaimInputsDoNotModifyPublications();

            OutboxClaimService claimService =
                    applicationContext.getBean(OutboxClaimService.class);
            OffsetDateTime databaseTimeBeforeClaim = runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.statement_timestamp()",
                    OffsetDateTime.class
            );
            assertThat(claimService.claimBatch("rls-outbox-test"))
                    .containsExactlyInAnyOrder(
                            new OutboxClaimService.ClaimedEvent(
                                    OUTBOX_EVENT_A,
                                    COMPANY_A
                            ),
                            new OutboxClaimService.ClaimedEvent(
                                    OUTBOX_EVENT_B,
                                    COMPANY_B
                            )
                    );
            OffsetDateTime databaseTimeAfterClaim = runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.statement_timestamp()",
                    OffsetDateTime.class
            );
            assertClaimLeaseUsesDatabaseClock(
                    OUTBOX_EVENT_A,
                    databaseTimeBeforeClaim,
                    databaseTimeAfterClaim
            );

            OutboxReadService readService =
                    applicationContext.getBean(OutboxReadService.class);
            assertThatThrownBy(
                    () -> readService.requirePublication(OUTBOX_EVENT_A, COMPANY_B)
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found");

            EventPublication publication =
                    readService.requirePublication(OUTBOX_EVENT_A, COMPANY_A);
            assertThat(publication.payloadJson()).isEqualTo("{}");

            assertOutboxLifecycleIgnoresSkewedApplicationClock();

            OutboxBacklogReader backlogReader =
                    applicationContext.getBean(OutboxBacklogReader.class);
            assertThat(backlogReader.countOutstanding()).isEqualTo(1);
            assertThat(backlogReader.findOldestOutstandingOccurredAt()).isPresent();
        } finally {
            migrationJdbc.execute("DELETE FROM event_consumption WHERE event_id IN ('"
                    + OUTBOX_EVENT_A + "', '" + OUTBOX_EVENT_B + "')");
            migrationJdbc.execute("DELETE FROM event_publication WHERE event_id IN ('"
                    + OUTBOX_EVENT_A + "', '" + OUTBOX_EVENT_B + "')");
            migrationJdbc.execute("DELETE FROM company WHERE company_id IN ('"
                    + COMPANY_A + "', '" + COMPANY_B + "')");
            migrationJdbc.execute(
                    "REVOKE EXECUTE ON FUNCTION " + function + " FROM " + quotedRole
            );
            migrationJdbc.execute(
                    "REVOKE EXECUTE ON FUNCTION " + countFunction + " FROM " + quotedRole
            );
            migrationJdbc.execute(
                    "REVOKE EXECUTE ON FUNCTION " + oldestFunction + " FROM " + quotedRole
            );
        }
    }

    private void assertInvalidOutboxClaimInputsDoNotModifyPublications() {
        Object[][] invalidArguments = {
                {null, 30_000L, 20, 8},
                {" ", 30_000L, 20, 8},
                {"\t\r\n", 30_000L, 20, 8},
                {"a".repeat(129), 30_000L, 20, 8},
                {"rls-outbox-test", null, 20, 8},
                {"rls-outbox-test", 0L, 20, 8},
                {"rls-outbox-test", 86_400_001L, 20, 8},
                {"rls-outbox-test", 30_000L, null, 8},
                {"rls-outbox-test", 30_000L, 501, 8},
                {"rls-outbox-test", 30_000L, 20, null},
                {"rls-outbox-test", 30_000L, 20, 0},
                {"rls-outbox-test", 30_000L, 20, 101}
        };

        for (Object[] arguments : invalidArguments) {
            Throwable thrown = catchThrowable(() -> runtimeJdbc.queryForList(
                    """
                    SELECT *
                    FROM public.bootstrap_claim_event_publications(
                        CAST(? AS TEXT),
                        CAST(? AS BIGINT),
                        CAST(? AS INTEGER),
                        CAST(? AS INTEGER)
                    )
                    """,
                    arguments
            ));
            assertThat(thrown).isInstanceOf(DataAccessException.class);
            Throwable rootCause =
                    ((DataAccessException) thrown).getMostSpecificCause();
            assertThat(rootCause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) rootCause).getSQLState()).isEqualTo("22023");
            assertOutboxProbeRemainsUnclaimed(OUTBOX_EVENT_A);
            assertOutboxProbeRemainsUnclaimed(OUTBOX_EVENT_B);
        }
    }

    private void assertOutboxLifecycleIgnoresSkewedApplicationClock() {
        String owner = "rls-outbox-test";
        OffsetDateTime beforeLifecycle = runtimeJdbc.queryForObject(
                "SELECT pg_catalog.statement_timestamp()",
                OffsetDateTime.class
        );

        OutboxHandlerTransaction handlerTransaction =
                applicationContext.getBean(OutboxHandlerTransaction.class);
        assertThat(handlerTransaction.deliver(
                OUTBOX_EVENT_A,
                COMPANY_A,
                owner,
                new NoOpProbeHandler()
        )).isTrue();
        applicationContext.getBean(OutboxCompletionTransaction.class)
                .complete(OUTBOX_EVENT_A, COMPANY_A, owner);

        OutboxFailureTransaction.FailureOutcome failureOutcome =
                applicationContext.getBean(OutboxFailureTransaction.class)
                        .recordFailure(
                                OUTBOX_EVENT_B,
                                COMPANY_B,
                                owner,
                                new RetryableEventHandlingException(
                                        "RLS_OUTBOX_PROBE_RETRY"
                                )
                        );
        assertThat(failureOutcome.retryScheduled()).isTrue();

        OffsetDateTime afterLifecycle = runtimeJdbc.queryForObject(
                "SELECT pg_catalog.statement_timestamp()",
                OffsetDateTime.class
        );
        String completedStatus = migrationJdbc.queryForObject(
                """
                SELECT status
                FROM event_publication
                WHERE event_id = ?
                """,
                String.class,
                OUTBOX_EVENT_A
        );
        OffsetDateTime completedAt = migrationJdbc.queryForObject(
                """
                SELECT completed_at
                FROM event_publication
                WHERE event_id = ?
                """,
                OffsetDateTime.class,
                OUTBOX_EVENT_A
        );
        assertThat(completedStatus).isEqualTo("COMPLETED");
        assertThat(completedAt.toInstant())
                .isBetween(
                        beforeLifecycle.toInstant(),
                        afterLifecycle.toInstant()
                );

        String retryStatus = migrationJdbc.queryForObject(
                """
                SELECT status
                FROM event_publication
                WHERE event_id = ?
                """,
                String.class,
                OUTBOX_EVENT_B
        );
        OffsetDateTime retryUpdatedAt = migrationJdbc.queryForObject(
                """
                SELECT updated_at
                FROM event_publication
                WHERE event_id = ?
                """,
                OffsetDateTime.class,
                OUTBOX_EVENT_B
        );
        OffsetDateTime nextAttemptAt = migrationJdbc.queryForObject(
                """
                SELECT next_attempt_at
                FROM event_publication
                WHERE event_id = ?
                """,
                OffsetDateTime.class,
                OUTBOX_EVENT_B
        );
        assertThat(retryStatus).isEqualTo("RETRY_WAIT");
        assertThat(retryUpdatedAt.toInstant())
                .isBetween(
                        beforeLifecycle.toInstant(),
                        afterLifecycle.toInstant()
                );
        assertThat(nextAttemptAt.toInstant())
                .isBetween(
                        beforeLifecycle.toInstant().plusSeconds(1),
                        afterLifecycle.toInstant().plusSeconds(1)
                );
    }

    private void assertOutboxProbeRemainsUnclaimed(UUID eventId) {
        Map<String, Object> state = migrationJdbc.queryForMap(
                """
                SELECT status, attempt_count, lease_owner, lease_expires_at
                FROM event_publication
                WHERE event_id = ?
                """,
                eventId
        );
        assertThat(state.get("status")).isEqualTo("PENDING");
        assertThat(state.get("attempt_count")).isEqualTo(0);
        assertThat(state.get("lease_owner")).isNull();
        assertThat(state.get("lease_expires_at")).isNull();
    }

    private void assertClaimLeaseUsesDatabaseClock(
            UUID eventId,
            OffsetDateTime databaseTimeBeforeClaim,
            OffsetDateTime databaseTimeAfterClaim
    ) {
        OffsetDateTime leaseExpiresAt = migrationJdbc.queryForObject(
                """
                SELECT lease_expires_at
                FROM event_publication
                WHERE event_id = ?
                """,
                OffsetDateTime.class,
                eventId
        );
        assertThat(leaseExpiresAt).isNotNull();
        assertThat(leaseExpiresAt.toInstant()).isBetween(
                databaseTimeBeforeClaim.toInstant().plusSeconds(30),
                databaseTimeAfterClaim.toInstant().plusSeconds(30)
        );
    }

    private ConfigurableApplicationContext startRestrictedRuntimeApplication() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", migrationUrl);
        properties.put("spring.datasource.username", runtimeRole);
        properties.put("spring.datasource.password", runtimePassword);
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.datasource.hikari.maximum-pool-size", "1");
        properties.put("spring.datasource.hikari.minimum-idle", "1");
        properties.put("spring.datasource.hikari.pool-name", "rls-runtime-test-pool");
        properties.put("spring.flyway.url", migrationUrl);
        properties.put("spring.flyway.user", migrationUsername);
        properties.put("spring.flyway.password", migrationPassword);
        properties.put(
                "spring.flyway.locations",
                "classpath:db/migration,classpath:db/migration-postgresql"
        );
        properties.put("app.demo-seed.enabled", "false");
        properties.put("app.database.tenant-context-mode", "postgresql");
        properties.put("server.port", "0");

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(
                new MapPropertySource("postgresql-runtime-role-test", properties)
        );

        SpringApplication application = new SpringApplication(
                ServerApplication.class,
                SkewedClockConfiguration.class
        );
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run();
    }

    private void insertOutboxProbe(UUID companyId, UUID eventId, String payload) {
        migrationJdbc.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (company_id) DO NOTHING
                """,
                companyId,
                "Outbox probe " + companyId
        );
        migrationJdbc.update(
                """
                INSERT INTO event_publication (
                    event_id, company_id, event_type, payload_version,
                    aggregate_type, aggregate_id, actor_type, request_id,
                    payload_json, status, attempt_count, next_attempt_at,
                    occurred_at, created_at, updated_at, version
                ) VALUES (
                    ?, ?, 'RlsOutboxProbe', '1',
                    'RlsProbe', ?, 'SYSTEM_RULE', ?,
                    ?, 'PENDING', 0, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                """,
                eventId,
                companyId,
                eventId,
                "rls-outbox-" + eventId,
                payload
        );
    }

    private ContextProbe bindAndRead(UUID companyId) {
        return transactionTemplate.execute(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
            return new ContextProbe(backendPid(), currentCompanyId());
        });
    }

    private ContextProbe readWithoutBinding() {
        return transactionTemplate.execute(
                status -> new ContextProbe(backendPid(), currentCompanyId())
        );
    }

    private Integer backendPid() {
        return ((Number) entityManager.createNativeQuery(
                "SELECT pg_catalog.pg_backend_pid()"
        ).getSingleResult()).intValue();
    }

    private String currentCompanyId() {
        Object currentCompanyValue = entityManager.createNativeQuery(
                """
                SELECT NULLIF(
                    pg_catalog.current_setting('app.company_id', true),
                    ''
                )
                """
        ).getSingleResult();
        return currentCompanyValue == null ? null : currentCompanyValue.toString();
    }

    private boolean hasTablePrivilege(String table, String privileges) {
        Boolean allowed = runtimeJdbc.queryForObject(
                """
                SELECT pg_catalog.has_table_privilege(
                    CURRENT_USER,
                    ?,
                    ?
                )
                """,
                Boolean.class,
                "public." + table,
                privileges
        );
        return Boolean.TRUE.equals(allowed);
    }

    private boolean hasFunctionPrivilege(String function, String privileges) {
        Boolean allowed = runtimeJdbc.queryForObject(
                """
                SELECT pg_catalog.has_function_privilege(
                    CURRENT_USER,
                    ?,
                    ?
                )
                """,
                Boolean.class,
                "public." + function,
                privileges
        );
        return Boolean.TRUE.equals(allowed);
    }

    private Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                migrationUrl,
                migrationUsername,
                migrationPassword
        );
    }

    private static boolean roleExists(Statement statement, String roleName)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = "
                        + quoteLiteral(roleName)
        )) {
            return resultSet.next();
        }
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private record RoleAttributes(
            boolean superuser,
            boolean createDatabase,
            boolean createRole,
            boolean inherit,
            boolean replication,
            boolean bypassRls
    ) {
    }

    private record ContextProbe(Integer backendPid, String companyId) {
    }

    @Configuration(proxyBeanMethods = false)
    static class SkewedClockConfiguration {

        @Bean
        @Primary
        Clock skewedApplicationClock() {
            return Clock.fixed(
                    Instant.parse("2099-01-01T00:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }

    private static final class NoOpProbeHandler implements DomainEventHandler {

        @Override
        public String handlerName() {
            return "rls-outbox-probe-handler";
        }

        @Override
        public boolean supports(String eventType) {
            return "RlsOutboxProbe".equals(eventType);
        }

        @Override
        public void handle(DomainEventEnvelope event) {
            // No-op: successful delivery is enough to verify lease validation.
        }
    }

    private static final class ExpectedTransactionFailure extends RuntimeException {
    }
}
