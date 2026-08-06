package com.fowoco.server.common.database;

import com.fowoco.server.ServerApplication;
import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import com.zaxxer.hikari.HikariDataSource;
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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

abstract class PostgreSqlRuntimeTimeoutIntegrationSupport {

    protected static final UUID FIXTURE_COMPANY_ID =
            UUID.fromString("64000000-0000-0000-0000-000000000001");
    protected static final String ORIGINAL_COMPANY_NAME = "Runtime timeout fixture";

    protected String migrationUrl;
    protected String migrationUsername;
    protected String migrationPassword;
    protected String runtimeRole;
    protected String runtimePassword;
    protected ConfigurableApplicationContext applicationContext;
    protected JdbcTemplate migrationJdbc;
    protected JdbcTemplate runtimeJdbc;
    protected HikariDataSource runtimeDataSource;
    protected TransactionTemplate transactionTemplate;
    private PostgreSqlRlsTestLock rlsTestLock;

    protected abstract String statementTimeout();

    protected abstract String lockTimeout();

    @BeforeAll
    void setUpRuntimeTimeoutFixture() throws SQLException {
        migrationUrl = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        migrationUsername = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        migrationPassword = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");

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

            runtimeRole = "timeout_runtime_test_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            runtimePassword = "Timeout-test-" + UUID.randomUUID();
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
                statement.execute(
                        "GRANT SELECT, UPDATE ON TABLE public.company TO " + quotedRole
                );
            }

            DataSource migrationDataSource = new DriverManagerDataSource(
                    migrationUrl,
                    migrationUsername,
                    migrationPassword
            );
            migrationJdbc = new JdbcTemplate(migrationDataSource);
            migrationJdbc.update(
                    """
                    INSERT INTO company (
                        company_id, name, status, created_at, updated_at, version
                    ) VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    ON CONFLICT (company_id) DO UPDATE SET
                        name = EXCLUDED.name,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    FIXTURE_COMPANY_ID,
                    ORIGINAL_COMPANY_NAME
            );

            applicationContext = startRuntimeApplication();
            runtimeDataSource = applicationContext.getBean(
                    "dataSource",
                    HikariDataSource.class
            );
            runtimeJdbc = new JdbcTemplate(runtimeDataSource);
            transactionTemplate = new TransactionTemplate(
                    applicationContext.getBean(PlatformTransactionManager.class)
            );
        } catch (Throwable setupFailure) {
            try {
                tearDownRuntimeTimeoutFixture();
            } catch (Throwable cleanupFailure) {
                setupFailure.addSuppressed(cleanupFailure);
            }
            throwFailure(setupFailure);
        }
    }

    @AfterAll
    void tearDownRuntimeTimeoutFixture() throws SQLException {
        Throwable failure = null;
        failure = runCleanupStep(failure, () -> {
            ConfigurableApplicationContext contextToClose = applicationContext;
            if (contextToClose != null) {
                contextToClose.close();
                applicationContext = null;
            }
        });
        failure = runCleanupStep(failure, () -> {
            if (migrationJdbc != null) {
                migrationJdbc.update(
                        "DELETE FROM company WHERE company_id = ?",
                        FIXTURE_COMPANY_ID
                );
            }
        });
        failure = runCleanupStep(failure, () -> {
            if (runtimeRole == null) {
                return;
            }
            try (Connection connection = migrationConnection();
                 Statement statement = connection.createStatement()) {
                if (roleExists(statement, runtimeRole)) {
                    statement.execute("DROP OWNED BY " + quoteIdentifier(runtimeRole));
                }
            }
        });
        failure = runCleanupStep(failure, () -> {
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
        });
        failure = runCleanupStep(failure, () -> {
            PostgreSqlRlsTestLock lockToClose = rlsTestLock;
            if (lockToClose != null) {
                lockToClose.close();
                rlsTestLock = null;
            }
        });
        if (failure != null) {
            throwFailure(failure);
        }
    }

    private static Throwable runCleanupStep(Throwable failure, CleanupStep step) {
        try {
            step.run();
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
        throw new IllegalStateException("Unexpected PostgreSQL test lifecycle failure", failure);
    }

    protected Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                migrationUrl,
                migrationUsername,
                migrationPassword
        );
    }

    protected int backendPid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT pg_catalog.pg_backend_pid()"
             )) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    protected String setting(Connection connection, String name) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_catalog.current_setting(?)"
        )) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    protected void restoreFixtureName() {
        migrationJdbc.update(
                "UPDATE company SET name = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE company_id = ?",
                ORIGINAL_COMPANY_NAME,
                FIXTURE_COMPANY_ID
        );
    }

    private ConfigurableApplicationContext startRuntimeApplication() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", migrationUrl);
        properties.put("spring.datasource.username", runtimeRole);
        properties.put("spring.datasource.password", runtimePassword);
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.datasource.hikari.maximum-pool-size", "1");
        properties.put("spring.datasource.hikari.minimum-idle", "0");
        properties.put("spring.datasource.hikari.initialization-fail-timeout", "5000");
        properties.put("spring.datasource.hikari.pool-name", "runtime-timeout-test-pool");
        properties.put("spring.flyway.url", migrationUrl);
        properties.put("spring.flyway.user", migrationUsername);
        properties.put("spring.flyway.password", migrationPassword);
        properties.put(
                "spring.flyway.locations",
                "classpath:db/migration,classpath:db/migration-postgresql"
        );
        properties.put("app.database.tenant-context-mode", "postgresql");
        properties.put(
                "app.database.runtime-timeout.statement-timeout",
                statementTimeout()
        );
        properties.put("app.database.runtime-timeout.lock-timeout", lockTimeout());
        properties.put("app.reliability.outbox.enabled", "false");
        properties.put("app.demo-seed.enabled", "false");
        properties.put("server.port", "0");

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(
                new MapPropertySource("postgresql-runtime-timeout-test", properties)
        );

        SpringApplication application = new SpringApplication(ServerApplication.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run();
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

    protected static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    protected static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    protected static String requiredEnvironmentVariable(String name) {
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
