package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.ServerApplication;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import com.fowoco.server.common.security.PostgreSqlRlsStateFixture;
import com.fowoco.server.common.security.TenantTransactionExecutor;
import com.fowoco.server.task.application.TaskResult;
import com.fowoco.server.task.application.TaskWorkflowService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class DemoSeedPostgreSqlApplicationIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TEST_COMPANY_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000003");
    private static final UUID REPRESENTATIVE_WORKER_ID =
            UUID.fromString("92000000-0000-0000-0000-000000000006");
    private static final UUID SHOWCASE_CASE_ID =
            UUID.fromString("94100000-0000-0000-0000-000000000001");
    private static final Map<String, Integer> EXPECTED_COUNTS = Map.ofEntries(
            Map.entry("user_account", 20),
            Map.entry("worker", 28),
            Map.entry("workflow_case", 21),
            Map.entry("task", 21),
            Map.entry("worker_document", 83),
            Map.entry("stored_file", 3),
            Map.entry("task_checklist_item", 60),
            Map.entry("approval_request", 15),
            Map.entry("task_transition_history", 54),
            Map.entry("external_submission", 6),
            Map.entry("task_evidence", 10),
            Map.entry("document_request_draft", 4),
            Map.entry("audit_event", 94)
    );
    private static final Map<String, Integer> EXPECTED_TEST_COUNTS = Map.of(
            "user_account", 3,
            "worker", 5,
            "workflow_case", 3,
            "task", 3,
            "worker_document", 8,
            "audit_event", 8
    );
    private static final List<String> SEED_TABLES = List.of(
            "company",
            "company_settings",
            "user_account",
            "worker",
            "workflow_case",
            "task",
            "worker_document",
            "stored_file",
            "task_checklist_item",
            "approval_request",
            "task_transition_history",
            "external_submission",
            "task_evidence",
            "document_request_draft",
            "document_request_draft_type",
            "audit_event"
    );
    private static final List<String> RUNTIME_READ_TABLES = List.of(
            "worker_archive"
    );

    @TempDir
    Path fileStoragePath;

    @Test
    void preservesExistingSeedWhenRlsIsEnabledAndRestrictedRuntimeRestarts()
            throws Exception {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String migrationUsername = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String migrationPassword = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");

        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                migrationUsername,
                migrationPassword
        )) {
            migrate(url, migrationUsername, migrationPassword);
            String runtimeRole = "demo_seed_runtime_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String runtimePassword = "Demo-seed-runtime-" + UUID.randomUUID();
            try (Connection migrationConnection = DriverManager.getConnection(
                    url,
                    migrationUsername,
                    migrationPassword
            ); PostgreSqlRlsStateFixture rlsState = PostgreSqlRlsStateFixture.capture(
                    migrationConnection,
                    policyTables(migrationConnection)
            )) {
                createRestrictedRuntimeRole(migrationConnection, runtimeRole, runtimePassword);
                try {
                    rlsState.disableRowLevelSecurityForFixtureSetup();
                    SeedSnapshot existingSeed;
                    Map<String, String> existingFiles;
                    try (ConfigurableApplicationContext context = startApplication(
                            url,
                            migrationUsername,
                            migrationPassword,
                            runtimeRole,
                            runtimePassword
                    )) {
                        JdbcTemplate migrationJdbc = migrationJdbc(
                                url,
                                migrationUsername,
                                migrationPassword
                        );
                        assertGoldenFlowStartState(migrationJdbc);
                        assertTaskDetailReadable(context);
                        existingSeed = snapshot(migrationJdbc);
                        existingFiles = fileSnapshot();
                    }

                    rlsState.enableRowLevelSecurity();
                    try (ConfigurableApplicationContext context = startApplication(
                            url,
                            migrationUsername,
                            migrationPassword,
                            runtimeRole,
                            runtimePassword
                    )) {
                        JdbcTemplate migrationJdbc = migrationJdbc(
                                url,
                                migrationUsername,
                                migrationPassword
                        );
                        assertRestrictedRuntime(context, migrationJdbc, runtimeRole);
                        assertGoldenFlowStartState(migrationJdbc);
                        assertTaskDetailReadable(context);
                        assertThat(snapshot(migrationJdbc)).isEqualTo(existingSeed);
                        assertThat(fileSnapshot()).isEqualTo(existingFiles);
                    }

                    JdbcTemplate migrationJdbc = migrationJdbc(
                            url,
                            migrationUsername,
                            migrationPassword
                    );
                    emulatePreviousReleaseSeed(migrationJdbc);
                    SeedSnapshot repairedSeed;
                    try (ConfigurableApplicationContext context = startApplication(
                            url,
                            migrationUsername,
                            migrationPassword,
                            runtimeRole,
                            runtimePassword
                    )) {
                        assertRestrictedRuntime(context, migrationJdbc, runtimeRole);
                        repairedSeed = snapshot(migrationJdbc);
                        assertSameStructure(repairedSeed, existingSeed);
                        assertThat(fileSnapshot()).isEqualTo(existingFiles);
                    }

                    try (ConfigurableApplicationContext context = startApplication(
                            url,
                            migrationUsername,
                            migrationPassword,
                            runtimeRole,
                            runtimePassword
                    )) {
                        assertRestrictedRuntime(context, migrationJdbc, runtimeRole);
                        assertThat(snapshot(migrationJdbc)).isEqualTo(repairedSeed);
                        assertThat(fileSnapshot()).isEqualTo(existingFiles);
                    }

                    rlsState.disableRowLevelSecurityForFixtureSetup();
                    deleteSeedFixtures(migrationJdbc);
                    rlsState.enableRowLevelSecurity();
                    SeedSnapshot freshRlsSeed;
                    try (ConfigurableApplicationContext context = startApplication(
                            url,
                            migrationUsername,
                            migrationPassword,
                            runtimeRole,
                            runtimePassword
                    )) {
                        assertRestrictedRuntime(context, migrationJdbc, runtimeRole);
                        freshRlsSeed = snapshot(migrationJdbc);
                        assertThat(fileSnapshot()).isEqualTo(existingFiles);
                    }
                    try (ConfigurableApplicationContext context = startApplication(
                            url,
                            migrationUsername,
                            migrationPassword,
                            runtimeRole,
                            runtimePassword
                    )) {
                        assertRestrictedRuntime(context, migrationJdbc, runtimeRole);
                        assertThat(snapshot(migrationJdbc)).isEqualTo(freshRlsSeed);
                        assertThat(fileSnapshot()).isEqualTo(existingFiles);
                    }
                } finally {
                    dropRestrictedRuntimeRole(migrationConnection, runtimeRole);
                }
            }
        }
    }

    private void emulatePreviousReleaseSeed(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update(
                "DELETE FROM audit_event WHERE company_id = ? AND audit_event_id IN (?, ?, ?, ?, ?, ?)",
                COMPANY_ID,
                UUID.fromString("96000000-0000-0000-0000-000000000097"),
                UUID.fromString("96000000-0000-0000-0000-000000000098"),
                UUID.fromString("96000000-0000-0000-0000-000000000099"),
                UUID.fromString("96000000-0000-0000-0000-000000000100"),
                UUID.fromString("96000000-0000-0000-0000-000000000101"),
                UUID.fromString("96000000-0000-0000-0000-000000000102")
        );
        jdbcTemplate.update(
                "DELETE FROM approval_request WHERE company_id = ? AND approval_request_id IN (?, ?, ?)",
                COMPANY_ID,
                UUID.fromString("94300000-0000-0000-0000-000000000014"),
                UUID.fromString("94300000-0000-0000-0000-000000000015"),
                UUID.fromString("94300000-0000-0000-0000-000000000016")
        );
        jdbcTemplate.update(
                "DELETE FROM task_transition_history WHERE company_id = ? "
                        + "AND transition_id IN (?, ?, ?, ?, ?, ?)",
                COMPANY_ID,
                UUID.fromString("94400000-0000-0000-0000-000000000053"),
                UUID.fromString("94400000-0000-0000-0000-000000000054"),
                UUID.fromString("94400000-0000-0000-0000-000000000057"),
                UUID.fromString("94400000-0000-0000-0000-000000000058"),
                UUID.fromString("94400000-0000-0000-0000-000000000059"),
                UUID.fromString("94400000-0000-0000-0000-000000000060")
        );
        jdbcTemplate.update(
                "UPDATE task_transition_history SET from_status = 'DRAFT' "
                        + "WHERE company_id = ? AND transition_id IN (?, ?, ?)",
                COMPANY_ID,
                UUID.fromString("94400000-0000-0000-0000-000000000006"),
                UUID.fromString("94400000-0000-0000-0000-000000000022"),
                UUID.fromString("94400000-0000-0000-0000-000000000025")
        );

        assertThat(countByCompany(jdbcTemplate, "approval_request", COMPANY_ID)).isEqualTo(12);
        assertThat(countByCompany(jdbcTemplate, "task_transition_history", COMPANY_ID)).isEqualTo(48);
        assertThat(countByCompany(jdbcTemplate, "audit_event", COMPANY_ID)).isEqualTo(88);
    }

    private void deleteSeedFixtures(JdbcTemplate jdbcTemplate) {
        List<String> dependencyOrder = List.of(
                "audit_event",
                "document_request_draft_type",
                "document_request_draft",
                "task_evidence",
                "external_submission",
                "task_transition_history",
                "approval_request",
                "task_checklist_item",
                "worker_document",
                "stored_file",
                "task",
                "workflow_case",
                "worker",
                "user_account",
                "company_settings",
                "company"
        );
        for (String table : dependencyOrder) {
            if ("document_request_draft_type".equals(table)) {
                jdbcTemplate.update(
                        """
                        DELETE FROM document_request_draft_type
                        WHERE draft_id IN (
                            SELECT draft_id
                            FROM document_request_draft
                            WHERE company_id IN (?, ?)
                        )
                        """,
                        COMPANY_ID,
                        TEST_COMPANY_ID
                );
                continue;
            }
            jdbcTemplate.update(
                    "DELETE FROM " + table + " WHERE company_id IN (?, ?)",
                    COMPANY_ID,
                    TEST_COMPANY_ID
            );
        }
        assertThat(countByCompany(jdbcTemplate, "company", COMPANY_ID)).isZero();
        assertThat(countByCompany(jdbcTemplate, "company", TEST_COMPANY_ID)).isZero();
    }

    private void migrate(String url, String username, String password) {
        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                .load()
                .migrate();
    }

    private JdbcTemplate migrationJdbc(String url, String username, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(url, username, password));
    }

    private List<String> policyTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     """
                     SELECT DISTINCT tablename
                     FROM pg_catalog.pg_policies
                     WHERE schemaname = 'public'
                     ORDER BY tablename
                     """
             )) {
            var tables = new java.util.ArrayList<String>();
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
            assertThat(tables).containsAll(SEED_TABLES);
            return List.copyOf(tables);
        }
    }

    private void createRestrictedRuntimeRole(
            Connection connection,
            String runtimeRole,
            String runtimePassword
    ) throws SQLException {
        String quotedRole = quoteIdentifier(runtimeRole);
        try (Statement statement = connection.createStatement()) {
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
                    "GRANT CONNECT ON DATABASE " + quoteIdentifier(connection.getCatalog())
                            + " TO " + quotedRole
            );
            statement.execute("GRANT USAGE ON SCHEMA public TO " + quotedRole);
            for (String table : SEED_TABLES) {
                statement.execute(
                        "GRANT SELECT, INSERT, UPDATE ON TABLE public."
                                + quoteIdentifier(table) + " TO " + quotedRole
                );
            }
            for (String table : RUNTIME_READ_TABLES) {
                statement.execute(
                        "GRANT SELECT ON TABLE public."
                                + quoteIdentifier(table) + " TO " + quotedRole
                );
            }
        }
    }

    private void dropRestrictedRuntimeRole(Connection connection, String runtimeRole)
            throws SQLException {
        String quotedRole = quoteIdentifier(runtimeRole);
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP OWNED BY " + quotedRole);
            statement.execute("DROP ROLE " + quotedRole);
        }
    }

    private void assertRestrictedRuntime(
            ConfigurableApplicationContext context,
            JdbcTemplate migrationJdbc,
            String runtimeRole
    ) {
        JdbcTemplate runtimeJdbc = context.getBean(JdbcTemplate.class);
        assertThat(runtimeJdbc.queryForObject("SELECT CURRENT_USER", String.class))
                .isEqualTo(runtimeRole);
        Map<String, Object> attributes = migrationJdbc.queryForMap(
                """
                SELECT rolsuper, rolbypassrls, rolinherit, rolcreatedb, rolcreaterole
                FROM pg_catalog.pg_roles
                WHERE rolname = ?
                """,
                runtimeRole
        );
        assertThat(attributes)
                .containsEntry("rolsuper", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolinherit", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false);
        List<String> ownedTables = migrationJdbc.queryForList(
                """
                SELECT relation.relname
                FROM pg_catalog.pg_class relation
                JOIN pg_catalog.pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relkind IN ('r', 'p')
                  AND pg_catalog.pg_get_userbyid(relation.relowner) = ?
                """,
                String.class,
                runtimeRole
        );
        assertThat(ownedTables).isEmpty();
        Integer disabledPolicyTables = migrationJdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT DISTINCT tablename
                    FROM pg_catalog.pg_policies
                    WHERE schemaname = 'public'
                ) policies
                JOIN pg_catalog.pg_class relation
                  ON relation.oid = ('public.' || policies.tablename)::regclass
                WHERE NOT relation.relrowsecurity
                """,
                Integer.class
        );
        assertThat(disabledPolicyTables).isZero();
        for (String table : SEED_TABLES) {
            for (String privilege : List.of("SELECT", "INSERT", "UPDATE")) {
                assertThat(runtimeJdbc.queryForObject(
                        "SELECT pg_catalog.has_table_privilege(CURRENT_USER, ?, ?)",
                        Boolean.class,
                        "public." + table,
                        privilege
                )).as("restricted runtime %s privilege for %s", privilege, table).isTrue();
            }
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.has_table_privilege(CURRENT_USER, ?, 'DELETE')",
                    Boolean.class,
                    "public." + table
            )).as("restricted runtime must not delete from %s", table).isFalse();
        }
        for (String table : RUNTIME_READ_TABLES) {
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.has_table_privilege(CURRENT_USER, ?, 'SELECT')",
                    Boolean.class,
                    "public." + table
            )).as("restricted runtime SELECT privilege for %s", table).isTrue();
            for (String privilege : List.of("INSERT", "UPDATE", "DELETE")) {
                assertThat(runtimeJdbc.queryForObject(
                        "SELECT pg_catalog.has_table_privilege(CURRENT_USER, ?, ?)",
                        Boolean.class,
                        "public." + table,
                        privilege
                )).as("restricted runtime must not have %s on %s", privilege, table).isFalse();
            }
        }
        assertThat(runtimeJdbc.queryForObject(
                "SELECT COUNT(*) FROM company",
                Integer.class
        )).isZero();

        TenantTransactionExecutor transactionExecutor =
                context.getBean(TenantTransactionExecutor.class);
        assertTenantCounts(transactionExecutor, runtimeJdbc, COMPANY_ID, 20, 28, 21);
        assertTenantCounts(transactionExecutor, runtimeJdbc, TEST_COMPANY_ID, 3, 5, 3);

        assertThat(runtimeJdbc.queryForObject(
                "SELECT COUNT(*) FROM company",
                Integer.class
        )).isZero();
        assertThat(runtimeJdbc.queryForObject(
                "SELECT NULLIF(pg_catalog.current_setting('app.company_id', true), '')",
                String.class
        )).isNull();
    }

    private void assertTenantCounts(
            TenantTransactionExecutor transactionExecutor,
            JdbcTemplate runtimeJdbc,
            UUID companyId,
            int userCount,
            int workerCount,
            int taskCount
    ) {
        AtomicReference<Map<String, Integer>> counts = new AtomicReference<>();
        transactionExecutor.execute(companyId, () -> counts.set(Map.of(
                "company", runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM company",
                        Integer.class
                ),
                "user", runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM user_account",
                        Integer.class
                ),
                "worker", runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM worker",
                        Integer.class
                ),
                "task", runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM task",
                        Integer.class
                )
        )));
        assertThat(counts).hasValue(Map.of(
                "company", 1,
                "user", userCount,
                "worker", workerCount,
                "task", taskCount
        ));
    }

    private Map<String, String> fileSnapshot() throws Exception {
        if (!Files.exists(fileStoragePath)) {
            return Map.of();
        }
        Map<String, String> snapshot = new LinkedHashMap<>();
        try (var paths = Files.walk(fileStoragePath)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                byte[] content = Files.readAllBytes(path);
                snapshot.put(
                        fileStoragePath.relativize(path).toString(),
                        content.length + ":" + sha256(content)
                );
            }
        }
        assertThat(snapshot).isNotEmpty();
        return Map.copyOf(snapshot);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private ConfigurableApplicationContext startApplication(
            String url,
            String migrationUsername,
            String migrationPassword,
            String runtimeUsername,
            String runtimePassword
    ) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", url);
        properties.put("spring.datasource.username", runtimeUsername);
        properties.put("spring.datasource.password", runtimePassword);
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.datasource.hikari.maximum-pool-size", "1");
        properties.put("spring.datasource.hikari.minimum-idle", "1");
        properties.put("spring.datasource.hikari.pool-name", "demo-seed-restricted-runtime-pool");
        properties.put("spring.flyway.url", url);
        properties.put("spring.flyway.user", migrationUsername);
        properties.put("spring.flyway.password", migrationPassword);
        properties.put(
                "spring.flyway.locations",
                "classpath:db/migration,classpath:db/migration-postgresql"
        );
        properties.put("spring.jpa.show-sql", "false");
        properties.put("app.database.tenant-context-mode", "postgresql");
        properties.put("app.reliability.outbox.enabled", "false");
        properties.put("app.ai-runtime.enabled", "false");
        properties.put("app.demo-seed.enabled", "true");
        properties.put("app.demo-seed.admin-password", "Demo-password-1!");
        properties.put("app.file-storage.local-path", fileStoragePath.toString());
        properties.put(
                "app.auth.jwt.secret-base64",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        );
        properties.put("app.auth.refresh-token.cookie.secure", "false");
        properties.put("server.port", "0");

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");
        environment.getPropertySources().addFirst(
                new MapPropertySource("postgresql-demo-seed-test", properties)
        );

        SpringApplication application = new SpringApplication(ServerApplication.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run();
    }

    private void assertGoldenFlowStartState(JdbcTemplate jdbcTemplate) {
        assertThat(countByCompany(jdbcTemplate, "worker", COMPANY_ID)).isEqualTo(28);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_case WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM worker_document
                WHERE worker_id = ? AND company_id = ?
                  AND document_type = 'PASSPORT_COPY'
                  AND submission_status = 'VERIFIED'
                  AND expiry_date > CURRENT_DATE
                  AND task_id IS NULL AND file_id IS NULL
                """,
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM worker_document
                WHERE worker_id = ? AND company_id = ?
                  AND document_type = 'ARC'
                  AND submission_status = 'MISSING'
                  AND expiry_date IS NULL
                  AND task_id IS NULL AND file_id IS NULL
                """,
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM worker_link link
                JOIN task linked_task
                  ON linked_task.task_id = link.task_id
                 AND linked_task.company_id = link.company_id
                WHERE linked_task.worker_id = ? AND link.company_id = ?
                """,
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isZero();

        for (String table : new String[] {
                "ai_run",
                "ai_attempt",
                "ai_question",
                "ai_candidate",
                "ai_candidate_decision_batch",
                "ai_candidate_decision"
        }) {
            assertThat(countByCompany(jdbcTemplate, table, COMPANY_ID))
                    .as("%s must not be pre-seeded for the Golden Flow", table)
                    .isZero();
        }
    }

    private void assertTaskDetailReadable(ConfigurableApplicationContext context) {
        TaskResult result = context.getBean(TaskWorkflowService.class).findById(
                TASK_ID,
                new ActorContext(ADMIN_USER_ID, COMPANY_ID, Set.of(UserRole.ADMIN))
        );

        assertThat(result.task().taskId()).isEqualTo(TASK_ID);
        assertThat(result.task().workerId()).isNotNull();
    }

    private SeedSnapshot snapshot(JdbcTemplate jdbcTemplate) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        EXPECTED_COUNTS.forEach((table, expected) -> {
            int actual = countByCompany(jdbcTemplate, table, COMPANY_ID);
            assertThat(actual).as("%s count", table).isEqualTo(expected);
            counts.put("demo:" + table, actual);
        });
        EXPECTED_TEST_COUNTS.forEach((table, expected) -> {
            int actual = countByCompany(jdbcTemplate, table, TEST_COMPANY_ID);
            assertThat(actual).as("test %s count", table).isEqualTo(expected);
            counts.put("test:" + table, actual);
        });
        assertThat(countByCompany(jdbcTemplate, "company", COMPANY_ID)).isEqualTo(1);
        assertThat(countByCompany(jdbcTemplate, "company", TEST_COMPANY_ID)).isEqualTo(1);
        assertThat(countByCompany(jdbcTemplate, "company_settings", COMPANY_ID)).isEqualTo(1);
        assertThat(countByCompany(jdbcTemplate, "company_settings", TEST_COMPANY_ID)).isEqualTo(1);
        Timestamp showcaseCreatedAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM workflow_case WHERE case_id = ? AND company_id = ?",
                Timestamp.class,
                SHOWCASE_CASE_ID,
                COMPANY_ID
        );
        assertThat(showcaseCreatedAt).isNotNull();
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String table : SEED_TABLES) {
            rows.put(table, seedRows(jdbcTemplate, table));
        }
        return new SeedSnapshot(Map.copyOf(counts), showcaseCreatedAt, Map.copyOf(rows));
    }

    private List<String> seedRows(JdbcTemplate jdbcTemplate, String table) {
        if ("document_request_draft_type".equals(table)) {
            return jdbcTemplate.queryForList(
                    """
                    SELECT to_jsonb(seed_row)::text
                    FROM public.document_request_draft_type AS seed_row
                    JOIN public.document_request_draft AS draft
                      ON draft.draft_id = seed_row.draft_id
                    WHERE draft.company_id IN (?, ?)
                    ORDER BY 1
                    """,
                    String.class,
                    COMPANY_ID,
                    TEST_COMPANY_ID
            );
        }
        return jdbcTemplate.queryForList(
                "SELECT (to_jsonb(seed_row) - 'password_hash')::text "
                        + "FROM public." + quoteIdentifier(table) + " AS seed_row "
                        + "WHERE company_id IN (?, ?) ORDER BY 1",
                String.class,
                COMPANY_ID,
                TEST_COMPANY_ID
        );
    }

    private void assertSameStructure(SeedSnapshot actual, SeedSnapshot expected) {
        assertThat(actual.counts()).isEqualTo(expected.counts());
        assertThat(actual.showcaseCreatedAt()).isEqualTo(expected.showcaseCreatedAt());
    }

    private int countByCompany(JdbcTemplate jdbcTemplate, String table, UUID companyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE company_id = ?",
                Integer.class,
                companyId
        );
        return count == null ? 0 : count;
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private record SeedSnapshot(
            Map<String, Integer> counts,
            Timestamp showcaseCreatedAt,
            Map<String, List<String>> rows
    ) {
    }
}
