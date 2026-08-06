package com.fowoco.server.common.security;

import com.fowoco.server.ServerApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class PostgreSqlRestrictedRoleHttpEnvironment implements AutoCloseable {

    private static final Map<String, String> TABLE_PRIVILEGES = Map.of(
            "company", "SELECT",
            "user_account", "SELECT",
            "refresh_token", "SELECT, INSERT, UPDATE",
            "worker", "SELECT, INSERT, UPDATE",
            "worker_link", "SELECT",
            "worker_response", "SELECT, INSERT",
            "audit_event", "SELECT, INSERT"
    );
    private static final String[] BOOTSTRAP_FUNCTIONS = {
            "public.bootstrap_company_id_by_normalized_email(TEXT)",
            "public.bootstrap_company_id_by_refresh_token_hash(TEXT)",
            "public.bootstrap_company_id_by_worker_link_token_hash(TEXT)"
    };

    private final String migrationUrl;
    private final String migrationUsername;
    private final String migrationPassword;
    private final PostgreSqlRestrictedRoleHttpDataFixture dataFixture;

    private String runtimeRole;
    private String runtimePassword;
    private Connection rlsStateConnection;
    private PostgreSqlRlsTestLock rlsTestLock;
    private PostgreSqlRlsStateFixture rlsStateFixture;
    private ConfigurableApplicationContext applicationContext;
    private DriverManagerDataSource migrationDataSource;
    private JdbcTemplate migrationJdbc;
    private JdbcTemplate runtimeJdbc;
    private boolean runtimeRolePrivilegesRevoked;
    private boolean cleanupStarted;

    private PostgreSqlRestrictedRoleHttpEnvironment(
            String migrationUrl,
            String migrationUsername,
            String migrationPassword,
            PostgreSqlRestrictedRoleHttpDataFixture dataFixture
    ) {
        this.migrationUrl = migrationUrl;
        this.migrationUsername = migrationUsername;
        this.migrationPassword = migrationPassword;
        this.dataFixture = dataFixture;
    }

    static PostgreSqlRestrictedRoleHttpEnvironment startFromEnvironment(
            PostgreSqlRestrictedRoleHttpDataFixture dataFixture,
            Class<?> testConfiguration
    ) throws SQLException {
        PostgreSqlRestrictedRoleHttpEnvironment environment =
                new PostgreSqlRestrictedRoleHttpEnvironment(
                        requiredEnvironmentVariable("POSTGRES_TEST_URL"),
                        requiredEnvironmentVariable("POSTGRES_TEST_USERNAME"),
                        requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD"),
                        dataFixture
                );
        environment.start(testConfiguration);
        return environment;
    }

    <T> T bean(Class<T> beanType) {
        if (applicationContext == null) {
            throw new IllegalStateException("Restricted HTTP application is not running");
        }
        return applicationContext.getBean(beanType);
    }

    int port() {
        if (!(applicationContext instanceof WebServerApplicationContext webContext)) {
            throw new IllegalStateException("Restricted HTTP web server is not running");
        }
        return webContext.getWebServer().getPort();
    }

    String runtimeRole() {
        return runtimeRole;
    }

    JdbcTemplate migrationJdbc() {
        return migrationJdbc;
    }

    JdbcTemplate runtimeJdbc() {
        return runtimeJdbc;
    }

    boolean hasTablePrivilege(String table, String privilege) {
        return Boolean.TRUE.equals(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.has_table_privilege(CURRENT_USER, ?, ?)",
                Boolean.class,
                "public." + table,
                privilege
        ));
    }

    boolean hasFunctionPrivilege(String function, String privilege) {
        return Boolean.TRUE.equals(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.has_function_privilege(CURRENT_USER, ?, ?)",
                Boolean.class,
                function,
                privilege
        ));
    }

    private void start(Class<?> testConfiguration) throws SQLException {
        rlsTestLock = PostgreSqlRlsTestLock.acquire(
                migrationUrl,
                migrationUsername,
                migrationPassword
        );
        try {
            Flyway.configure()
                    .dataSource(migrationUrl, migrationUsername, migrationPassword)
                    .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                    .load()
                    .migrate();

            migrationDataSource = new DriverManagerDataSource(
                    migrationUrl,
                    migrationUsername,
                    migrationPassword
            );
            migrationJdbc = new JdbcTemplate(migrationDataSource);
            rlsStateConnection = migrationConnection();
            rlsStateFixture = PostgreSqlRlsStateFixture.capture(
                    rlsStateConnection,
                    dataFixture.rlsTables()
            );
            rlsStateFixture.disableRowLevelSecurityForFixtureSetup();
            dataFixture.prepare(migrationDataSource);
            createRestrictedRuntimeRole();
            rlsStateFixture.enableRowLevelSecurity();
            applicationContext = startApplication(testConfiguration);
            runtimeJdbc = new JdbcTemplate(bean(javax.sql.DataSource.class));
        } catch (Throwable setupFailure) {
            try {
                close();
            } catch (Throwable cleanupFailure) {
                setupFailure.addSuppressed(cleanupFailure);
            }
            throwFailure(setupFailure);
        }
    }

    @Override
    public void close() throws SQLException {
        if (cleanupStarted) {
            return;
        }
        cleanupStarted = true;

        Throwable failure = null;
        failure = runCleanupStep(failure, () -> {
            if (applicationContext != null) {
                applicationContext.close();
                applicationContext = null;
                runtimeJdbc = null;
            }
        });
        failure = runCleanupStep(failure, dataFixture::cleanup);
        failure = runCleanupStep(failure, this::dropRuntimeRoleOwnedObjects);
        failure = runCleanupStep(failure, this::dropRuntimeRole);
        failure = runCleanupStep(failure, this::restoreRlsState);
        failure = runCleanupStep(failure, () -> {
            if (rlsStateConnection != null) {
                rlsStateConnection.close();
                rlsStateConnection = null;
            }
        });
        failure = runCleanupStep(failure, () -> {
            if (rlsTestLock != null) {
                rlsTestLock.close();
                rlsTestLock = null;
            }
        });
        if (failure != null) {
            throwFailure(failure);
        }
    }

    private void createRestrictedRuntimeRole() throws SQLException {
        runtimeRole = "restricted_http_test_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        runtimePassword = "Restricted-http-test-" + UUID.randomUUID();
        runtimeRolePrivilegesRevoked = false;

        try (Statement statement = rlsStateConnection.createStatement()) {
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
                            + quoteIdentifier(rlsStateConnection.getCatalog())
                            + " TO "
                            + quotedRole
            );
            statement.execute("GRANT USAGE ON SCHEMA public TO " + quotedRole);
            for (Map.Entry<String, String> entry : TABLE_PRIVILEGES.entrySet()) {
                statement.execute(
                        "GRANT " + entry.getValue()
                                + " ON TABLE public." + quoteIdentifier(entry.getKey())
                                + " TO " + quotedRole
                );
            }
            for (String function : BOOTSTRAP_FUNCTIONS) {
                statement.execute(
                        "GRANT EXECUTE ON FUNCTION " + function + " TO " + quotedRole
                );
            }
        }
    }

    private ConfigurableApplicationContext startApplication(Class<?> testConfiguration) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", migrationUrl);
        properties.put("spring.datasource.username", runtimeRole);
        properties.put("spring.datasource.password", runtimePassword);
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.datasource.hikari.maximum-pool-size", "1");
        properties.put("spring.datasource.hikari.minimum-idle", "1");
        properties.put("spring.datasource.hikari.initialization-fail-timeout", "5000");
        properties.put("spring.datasource.hikari.pool-name", "restricted-role-http-e2e-pool");
        properties.put("spring.flyway.enabled", "false");
        properties.put("app.database.tenant-context-mode", "postgresql");
        properties.put("app.reliability.outbox.enabled", "false");
        properties.put("app.demo-seed.enabled", "false");
        properties.put("server.port", "0");

        StandardEnvironment springEnvironment = new StandardEnvironment();
        springEnvironment.setActiveProfiles("test");
        springEnvironment.getPropertySources().addFirst(
                new MapPropertySource("restricted-role-http-e2e", properties)
        );

        SpringApplication application = new SpringApplication(
                ServerApplication.class,
                testConfiguration
        );
        application.setEnvironment(springEnvironment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run();
    }

    private void dropRuntimeRoleOwnedObjects() throws SQLException {
        if (runtimeRole == null) {
            runtimeRolePrivilegesRevoked = true;
            return;
        }
        try (Connection connection = migrationConnection();
             Statement statement = connection.createStatement()) {
            if (roleExists(statement, runtimeRole)) {
                statement.execute("DROP OWNED BY " + quoteIdentifier(runtimeRole));
            }
            runtimeRolePrivilegesRevoked = true;
        }
    }

    private void dropRuntimeRole() throws SQLException {
        if (runtimeRole == null) {
            return;
        }
        try (Connection connection = migrationConnection();
             Statement statement = connection.createStatement()) {
            if (roleExists(statement, runtimeRole)) {
                statement.execute("DROP ROLE " + quoteIdentifier(runtimeRole));
            }
            runtimeRole = null;
        }
    }

    private void restoreRlsState() throws SQLException {
        if (rlsStateFixture == null) {
            return;
        }
        if (runtimeRole != null && !runtimeRolePrivilegesRevoked) {
            throw new SQLException(
                    "Refusing to restore RLS while the restricted HTTP role has privileges"
            );
        }
        rlsStateFixture.restore();
        rlsStateFixture = null;
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

    private static Throwable runCleanupStep(Throwable failure, CleanupStep cleanupStep) {
        try {
            cleanupStep.run();
        } catch (Throwable cleanupFailure) {
            if (failure == null) {
                return cleanupFailure;
            }
            failure.addSuppressed(cleanupFailure);
        }
        return failure;
    }

    private static void throwFailure(Throwable failure) throws SQLException {
        if (failure instanceof SQLException sqlException) {
            throw sqlException;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Restricted HTTP test lifecycle failed", failure);
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

    @FunctionalInterface
    private interface CleanupStep {

        void run() throws Exception;
    }
}
