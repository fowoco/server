package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class PostgreSqlRlsIsolationTest {

    private static final UUID COMPANY_A =
            UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("b1000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A =
            UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_B =
            UUID.fromString("b2000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A_NEW =
            UUID.fromString("a2000000-0000-0000-0000-000000000003");
    private static final UUID WORKER_B_NEW =
            UUID.fromString("b2000000-0000-0000-0000-000000000004");

    @Test
    void restrictedRoleEnforcesTenantCrudAndFailsClosedWithoutValidContext()
            throws SQLException {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String migrationUsername = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String migrationPassword = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");
        Flyway.configure()
                .dataSource(url, migrationUsername, migrationPassword)
                .locations(
                        "classpath:db/migration",
                        "classpath:db/migration-postgresql"
                )
                .load()
                .migrate();

        String runtimeRole = "rls_isolation_test_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String runtimePassword = "Rls-isolation-" + UUID.randomUUID();

        try (Connection migrationConnection = DriverManager.getConnection(
                url,
                migrationUsername,
                migrationPassword
        )) {
            prepareFixture(migrationConnection, runtimeRole, runtimePassword);
            try (Connection runtimeConnection = DriverManager.getConnection(
                    url,
                    runtimeRole,
                    runtimePassword
            )) {
                assertMissingAndInvalidContextFailClosed(runtimeConnection);
                assertTenantCrudIsolation(runtimeConnection);
                assertCommittedContextDoesNotLeak(runtimeConnection);
            } finally {
                restoreFixture(migrationConnection, runtimeRole);
            }
        }
    }

    private void prepareFixture(
            Connection connection,
            String runtimeRole,
            String runtimePassword
    ) throws SQLException {
        try (Statement statement = connection.createStatement()) {
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
                    "GRANT SELECT, INSERT, UPDATE, DELETE "
                            + "ON TABLE public.company, public.worker TO "
                            + quotedRole
            );

            deleteFixtureRows(statement);
            statement.execute("""
                    INSERT INTO company (company_id, name, status)
                    VALUES
                        ('%s', 'RLS Tenant A', 'ACTIVE'),
                        ('%s', 'RLS Tenant B', 'ACTIVE')
                    """.formatted(COMPANY_A, COMPANY_B));
            statement.execute("""
                    INSERT INTO worker (
                        worker_id, company_id, display_name, work_status
                    ) VALUES
                        ('%s', '%s', 'Worker A', 'ACTIVE'),
                        ('%s', '%s', 'Worker B', 'ACTIVE')
                    """.formatted(WORKER_A, COMPANY_A, WORKER_B, COMPANY_B));

            statement.execute("ALTER TABLE public.company ENABLE ROW LEVEL SECURITY");
            statement.execute("ALTER TABLE public.worker ENABLE ROW LEVEL SECURITY");
        }
    }

    private void assertMissingAndInvalidContextFailClosed(Connection connection)
            throws SQLException {
        connection.setAutoCommit(false);
        try {
            assertThat(workerCount(connection)).isZero();

            setTenantContext(connection, "");
            assertThat(workerCount(connection)).isZero();
            connection.rollback();

            setTenantContext(connection, "not-a-uuid");
            assertSqlState(connection, "22P02", "SELECT COUNT(*) FROM public.worker");
        } finally {
            connection.rollback();
        }
    }

    private void assertTenantCrudIsolation(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            setTenantContext(connection, COMPANY_A.toString());

            assertThat(workerIds(connection)).containsExactly(WORKER_A);
            assertThat(executeUpdate(
                    connection,
                    """
                    INSERT INTO worker (
                        worker_id, company_id, display_name, work_status
                    ) VALUES (?, ?, 'Worker A New', 'ACTIVE')
                    """,
                    WORKER_A_NEW,
                    COMPANY_A
            )).isOne();
            assertThat(executeUpdate(
                    connection,
                    "UPDATE worker SET display_name = 'Worker A Updated' WHERE worker_id = ?",
                    WORKER_A_NEW
            )).isOne();

            assertSqlState(
                    connection,
                    "42501",
                    """
                    INSERT INTO worker (
                        worker_id, company_id, display_name, work_status
                    ) VALUES (
                        '%s', '%s', 'Forbidden Worker B', 'ACTIVE'
                    )
                    """.formatted(WORKER_B_NEW, COMPANY_B)
            );
            assertSqlState(
                    connection,
                    "42501",
                    """
                    UPDATE worker
                    SET company_id = '%s'
                    WHERE worker_id = '%s'
                    """.formatted(COMPANY_B, WORKER_A_NEW)
            );

            assertThat(executeUpdate(
                    connection,
                    "UPDATE worker SET display_name = 'Hidden Update' WHERE worker_id = ?",
                    WORKER_B
            )).isZero();
            assertThat(executeUpdate(
                    connection,
                    "DELETE FROM worker WHERE worker_id = ?",
                    WORKER_B
            )).isZero();
            assertThat(executeUpdate(
                    connection,
                    "DELETE FROM worker WHERE worker_id = ?",
                    WORKER_A_NEW
            )).isOne();
        } finally {
            connection.rollback();
        }
    }

    private void assertCommittedContextDoesNotLeak(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        setTenantContext(connection, COMPANY_A.toString());
        assertThat(workerIds(connection)).containsExactly(WORKER_A);
        connection.commit();

        assertThat(workerCount(connection)).isZero();
        setTenantContext(connection, COMPANY_B.toString());
        assertThat(workerIds(connection)).containsExactly(WORKER_B);
        connection.rollback();
    }

    private void restoreFixture(Connection connection, String runtimeRole) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE public.worker DISABLE ROW LEVEL SECURITY");
            statement.execute("ALTER TABLE public.company DISABLE ROW LEVEL SECURITY");
            deleteFixtureRows(statement);
            String quotedRole = quoteIdentifier(runtimeRole);
            statement.execute("DROP OWNED BY " + quotedRole);
            statement.execute("DROP ROLE " + quotedRole);
        }
    }

    private void deleteFixtureRows(Statement statement) throws SQLException {
        statement.execute("""
                DELETE FROM worker
                WHERE worker_id IN (
                    '%s', '%s', '%s', '%s'
                )
                """.formatted(WORKER_A, WORKER_B, WORKER_A_NEW, WORKER_B_NEW));
        statement.execute("""
                DELETE FROM company
                WHERE company_id IN ('%s', '%s')
                """.formatted(COMPANY_A, COMPANY_B));
    }

    private void setTenantContext(Connection connection, String companyId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_catalog.set_config('app.company_id', ?, true)"
        )) {
            statement.setString(1, companyId);
            statement.executeQuery();
        }
    }

    private int workerCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM public.worker"
             )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private java.util.List<UUID> workerIds(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT worker_id FROM public.worker ORDER BY worker_id"
             )) {
            java.util.List<UUID> workerIds = new java.util.ArrayList<>();
            while (resultSet.next()) {
                workerIds.add(resultSet.getObject(1, UUID.class));
            }
            return java.util.List.copyOf(workerIds);
        }
    }

    private int executeUpdate(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement.executeUpdate();
        }
    }

    private void assertSqlState(Connection connection, String expectedSqlState, String sql)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException failure = null;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            failure = exception;
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }

        assertThat((Throwable) failure)
                .as("SQL must fail with SQLSTATE %s", expectedSqlState)
                .isNotNull();
        assertThat(failure.getSQLState()).isEqualTo(expectedSqlState);
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
}
