package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.ServerApplication;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
                    ON TABLE public.company, public.user_account, public.refresh_token
                    TO %s
                    """.formatted(quotedRole));
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

        for (String table : new String[]{"company", "user_account", "refresh_token"}) {
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
        properties.put("server.port", "0");

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(
                new MapPropertySource("postgresql-runtime-role-test", properties)
        );

        SpringApplication application = new SpringApplication(ServerApplication.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run();
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

    private static final class ExpectedTransactionFailure extends RuntimeException {
    }
}
