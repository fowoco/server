package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.ServerApplication;
import com.fowoco.server.common.security.PostgreSqlRlsStateFixture;
import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class PostgreSqlWorkerRestrictedRuntimeFixture implements AutoCloseable {

    private final String migrationUrl;
    private final String migrationUsername;
    private final String migrationPassword;
    private final PostgreSqlWorkerDataFixture dataFixture;

    private String runtimeRole;
    private String runtimePassword;
    private Connection rlsStateConnection;
    private PostgreSqlRlsTestLock rlsTestLock;
    private PostgreSqlRlsStateFixture rlsStateFixture;
    private ConfigurableApplicationContext applicationContext;
    private boolean runtimeRolePrivilegesRevoked;
    private boolean cleanupStarted;

    private PostgreSqlWorkerRestrictedRuntimeFixture(
            String migrationUrl,
            String migrationUsername,
            String migrationPassword,
            PostgreSqlWorkerDataFixture dataFixture
    ) {
        this.migrationUrl = Objects.requireNonNull(migrationUrl, "migrationUrl must not be null");
        this.migrationUsername = Objects.requireNonNull(
                migrationUsername,
                "migrationUsername must not be null"
        );
        this.migrationPassword = Objects.requireNonNull(
                migrationPassword,
                "migrationPassword must not be null"
        );
        this.dataFixture = Objects.requireNonNull(dataFixture, "dataFixture must not be null");
    }

    static PostgreSqlWorkerRestrictedRuntimeFixture startFromEnvironment(
            PostgreSqlWorkerDataFixture dataFixture
    ) throws SQLException {
        PostgreSqlWorkerRestrictedRuntimeFixture fixture =
                new PostgreSqlWorkerRestrictedRuntimeFixture(
                        requiredEnvironmentVariable("POSTGRES_TEST_URL"),
                        requiredEnvironmentVariable("POSTGRES_TEST_USERNAME"),
                        requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD"),
                        dataFixture
                );
        fixture.start();
        return fixture;
    }

    <T> T bean(Class<T> beanType) {
        ConfigurableApplicationContext context = applicationContext;
        if (context == null) {
            throw new IllegalStateException("Restricted runtime application is not running");
        }
        return context.getBean(beanType);
    }

    private void start() throws SQLException {
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

            rlsStateConnection = migrationConnection();
            rlsStateFixture = PostgreSqlRlsStateFixture.capture(
                    rlsStateConnection,
                    dataFixture.rlsTables()
            );
            rlsStateFixture.disableRowLevelSecurityForFixtureSetup();
            createRestrictedRuntimeRole();

            DriverManagerDataSource migrationDataSource = new DriverManagerDataSource(
                    migrationUrl,
                    migrationUsername,
                    migrationPassword
            );
            dataFixture.prepare(migrationDataSource);
            rlsStateFixture.enableRowLevelSecurity();
            applicationContext = startRestrictedRuntimeApplication();
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
            ConfigurableApplicationContext contextToClose = applicationContext;
            if (contextToClose != null) {
                contextToClose.close();
                applicationContext = null;
            }
        });
        failure = runCleanupStep(failure, dataFixture::cleanup);
        failure = runCleanupStep(failure, this::dropRuntimeRoleOwnedObjects);
        failure = runCleanupStep(failure, this::dropRuntimeRole);
        failure = runCleanupStep(failure, this::restoreRlsState);
        failure = runCleanupStep(failure, () -> {
            Connection connectionToClose = rlsStateConnection;
            if (connectionToClose != null) {
                connectionToClose.close();
                rlsStateConnection = null;
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

    private void createRestrictedRuntimeRole() throws SQLException {
        runtimeRole = "worker_repository_rls_test_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        runtimePassword = "Worker-repository-test-" + UUID.randomUUID();
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
            statement.execute("GRANT SELECT ON TABLE public.company TO " + quotedRole);
            statement.execute(
                    "GRANT SELECT, INSERT, UPDATE, DELETE "
                            + "ON TABLE public.worker TO "
                            + quotedRole
            );
        }
    }

    private ConfigurableApplicationContext startRestrictedRuntimeApplication() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", migrationUrl);
        properties.put("spring.datasource.username", runtimeRole);
        properties.put("spring.datasource.password", runtimePassword);
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.datasource.hikari.maximum-pool-size", "1");
        properties.put("spring.datasource.hikari.minimum-idle", "1");
        properties.put("spring.datasource.hikari.initialization-fail-timeout", "5000");
        properties.put("spring.datasource.hikari.pool-name", "worker-repository-rls-test-pool");
        properties.put("spring.flyway.enabled", "false");
        properties.put("app.database.tenant-context-mode", "postgresql");
        properties.put("app.reliability.outbox.enabled", "false");
        properties.put("app.demo-seed.enabled", "false");
        properties.put("server.port", "0");

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(
                new MapPropertySource("postgresql-worker-repository-rls-test", properties)
        );

        SpringApplication application = new SpringApplication(ServerApplication.class);
        application.setEnvironment(environment);
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
        PostgreSqlRlsStateFixture fixtureToRestore = rlsStateFixture;
        if (fixtureToRestore == null) {
            return;
        }
        if (runtimeRole != null && !runtimeRolePrivilegesRevoked) {
            throw new SQLException(
                    "Refusing to restore the original RLS state while the restricted test role "
                            + "still has table privileges"
            );
        }
        fixtureToRestore.restore();
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
        throw new IllegalStateException(
                "Unexpected PostgreSQL repository RLS test lifecycle failure",
                failure
        );
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
