package com.fowoco.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class PostgreSqlMigrationTests {

    private static final String COMPANY_A = "10000000-0000-0000-0000-000000000001";
    private static final String COMPANY_B = "20000000-0000-0000-0000-000000000002";
    private static final String USER_A = "11000000-0000-0000-0000-000000000001";
    private static final String USER_B = "22000000-0000-0000-0000-000000000002";
    private static final String TOKEN_HASH_A = "a".repeat(64);

    @Test
    void migrationsApplyCanonicalAuthSchemaOnPostgreSql() throws SQLException {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations(
                        "classpath:db/migration",
                        "classpath:db/migration-postgresql"
                )
                .load();

        flyway.migrate();
        flyway.validate();

        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertSchemaContract(connection);
            connection.setAutoCommit(false);
            try {
                assertConstraintBehavior(connection);
            } finally {
                connection.rollback();
            }
        }
    }

    private void assertSchemaContract(Connection connection) throws SQLException {
        assertThat(tableNames(connection))
                .contains("company", "user_account", "refresh_token");

        assertThat(columnSpecs(connection, "company"))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("status", new ColumnSpec("varchar", false))
                .containsEntry("version", new ColumnSpec("int8", false))
                .doesNotContainKey("company_name");
        assertThat(columnSpecs(connection, "user_account"))
                .containsEntry("user_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("normalized_email", new ColumnSpec("varchar", false))
                .containsEntry("password_hash", new ColumnSpec("varchar", false))
                .containsEntry("role", new ColumnSpec("varchar", false))
                .containsEntry("status", new ColumnSpec("varchar", false))
                .doesNotContainKeys("company_name", "id", "password");
        assertThat(columnSpecs(connection, "refresh_token"))
                .containsEntry("refresh_token_id", new ColumnSpec("uuid", false))
                .containsEntry("user_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("token_family_id", new ColumnSpec("uuid", false))
                .containsEntry("token_hash", new ColumnSpec("varchar", false))
                .containsEntry("expires_at", new ColumnSpec("timestamptz", false))
                .containsEntry("used_at", new ColumnSpec("timestamptz", true))
                .containsEntry("revoked_at", new ColumnSpec("timestamptz", true))
                .containsEntry("version", new ColumnSpec("int8", false));

        assertThat(constraintNames(connection))
                .contains(
                        "pk_company",
                        "pk_user_account",
                        "fk_user_account_company",
                        "uq_user_account_normalized_email",
                        "uq_user_account_user_company",
                        "pk_refresh_token",
                        "uq_refresh_token_hash",
                        "fk_refresh_token_user_company"
                );
        assertThat(indexNames(connection))
                .contains(
                        "idx_user_account_company",
                        "idx_refresh_token_company_user",
                        "idx_refresh_token_family_revoked",
                        "idx_refresh_token_expires_at"
                );
    }

    private void assertConstraintBehavior(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO company (company_id, name, status)
                VALUES
                    ('%s', 'Tenant A', 'ACTIVE'),
                    ('%s', 'Tenant B', 'ACTIVE')
                """.formatted(COMPANY_A, COMPANY_B));
        execute(connection, """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status
                ) VALUES
                    ('%s', '%s', 'admin.a@example.com', 'admin.a@example.com',
                     'test-password-hash-a', 'ADMIN', 'ACTIVE'),
                    ('%s', '%s', 'viewer.b@example.com', 'viewer.b@example.com',
                     'test-password-hash-b', 'VIEWER', 'ACTIVE')
                """.formatted(USER_A, COMPANY_A, USER_B, COMPANY_B));
        execute(connection, """
                INSERT INTO refresh_token (
                    refresh_token_id, user_id, company_id,
                    token_family_id, token_hash, expires_at
                ) VALUES (
                    '31000000-0000-0000-0000-000000000001',
                    '%s', '%s',
                    '32000000-0000-0000-0000-000000000001',
                    '%s', CURRENT_TIMESTAMP + INTERVAL '1 day'
                )
                """.formatted(USER_A, COMPANY_A, TOKEN_HASH_A));

        assertSqlState(connection, "23505", """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status
                ) VALUES (
                    '41000000-0000-0000-0000-000000000001', '%s',
                    'ADMIN.A@example.com', 'admin.a@example.com',
                    'test-password-hash', 'HR', 'ACTIVE'
                )
                """.formatted(COMPANY_A));
        assertSqlState(connection, "23514", """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status
                ) VALUES (
                    '42000000-0000-0000-0000-000000000001', '%s',
                    'owner@example.com', 'owner@example.com',
                    'test-password-hash', 'OWNER', 'ACTIVE'
                )
                """.formatted(COMPANY_A));
        assertSqlState(connection, "23514", """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status
                ) VALUES (
                    '43000000-0000-0000-0000-000000000001', '%s',
                    'mixed@example.com', 'different@example.com',
                    'test-password-hash', 'HR', 'ACTIVE'
                )
                """.formatted(COMPANY_A));
        assertSqlState(connection, "23503", """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status
                ) VALUES (
                    '44000000-0000-0000-0000-000000000001',
                    '40000000-0000-0000-0000-000000000099',
                    'orphan@example.com', 'orphan@example.com',
                    'test-password-hash', 'HR', 'ACTIVE'
                )
                """);
        assertSqlState(connection, "23503", """
                INSERT INTO refresh_token (
                    refresh_token_id, user_id, company_id,
                    token_family_id, token_hash, expires_at
                ) VALUES (
                    '51000000-0000-0000-0000-000000000001',
                    '%s', '%s',
                    '52000000-0000-0000-0000-000000000001',
                    '%s', CURRENT_TIMESTAMP + INTERVAL '1 day'
                )
                """.formatted(USER_A, COMPANY_B, "b".repeat(64)));
        assertSqlState(connection, "23514", """
                INSERT INTO refresh_token (
                    refresh_token_id, user_id, company_id,
                    token_family_id, token_hash, expires_at
                ) VALUES (
                    '53000000-0000-0000-0000-000000000001',
                    '%s', '%s',
                    '54000000-0000-0000-0000-000000000001',
                    '%s', CURRENT_TIMESTAMP + INTERVAL '1 day'
                )
                """.formatted(USER_A, COMPANY_A, "c".repeat(63)));
        assertSqlState(connection, "23514", """
                INSERT INTO refresh_token (
                    refresh_token_id, user_id, company_id,
                    token_family_id, token_hash, expires_at
                ) VALUES (
                    '55000000-0000-0000-0000-000000000001',
                    '%s', '%s',
                    '56000000-0000-0000-0000-000000000001',
                    '%s', CURRENT_TIMESTAMP + INTERVAL '1 day'
                )
                """.formatted(USER_A, COMPANY_A, "D".repeat(64)));
        assertSqlState(connection, "23514", """
                INSERT INTO refresh_token (
                    refresh_token_id, user_id, company_id,
                    token_family_id, token_hash, created_at, updated_at, expires_at
                ) VALUES (
                    '57000000-0000-0000-0000-000000000001',
                    '%s', '%s',
                    '58000000-0000-0000-0000-000000000001',
                    '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP - INTERVAL '1 second'
                )
                """.formatted(USER_A, COMPANY_A, "e".repeat(64)));
        assertSqlState(connection, "23505", """
                INSERT INTO refresh_token (
                    refresh_token_id, user_id, company_id,
                    token_family_id, token_hash, expires_at
                ) VALUES (
                    '59000000-0000-0000-0000-000000000001',
                    '%s', '%s',
                    '59000000-0000-0000-0000-000000000002',
                    '%s', CURRENT_TIMESTAMP + INTERVAL '1 day'
                )
                """.formatted(USER_A, COMPANY_A, TOKEN_HASH_A));
        assertSqlState(
                connection,
                "23503",
                "DELETE FROM company WHERE company_id = '%s'".formatted(COMPANY_A)
        );
    }

    private Set<String> tableNames(Connection connection) throws SQLException {
        return queryStrings(
                connection,
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                """
        );
    }

    private Map<String, ColumnSpec> columnSpecs(Connection connection, String tableName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT column_name, udt_name, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                """
        )) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, ColumnSpec> columns = new LinkedHashMap<>();
                while (resultSet.next()) {
                    columns.put(
                            resultSet.getString("column_name"),
                            new ColumnSpec(
                                    resultSet.getString("udt_name"),
                                    "YES".equals(resultSet.getString("is_nullable"))
                            )
                    );
                }
                return columns;
            }
        }
    }

    private Set<String> constraintNames(Connection connection) throws SQLException {
        return queryStrings(
                connection,
                """
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                """
        );
    }

    private Set<String> indexNames(Connection connection) throws SQLException {
        return queryStrings(
                connection,
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                """
        );
    }

    private void assertSqlState(Connection connection, String expectedSqlState, String sql)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException failure = null;
        try {
            execute(connection, sql);
        } catch (SQLException exception) {
            failure = exception;
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }

        assertThat((Object) failure)
                .as("SQL must fail with SQLSTATE %s", expectedSqlState)
                .isNotNull();
        assertThat(failure.getSQLState()).isEqualTo(expectedSqlState);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Set<String> queryStrings(Connection connection, String sql, String... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                Set<String> values = new LinkedHashSet<>();
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
                return values;
            }
        }
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private record ColumnSpec(String udtName, boolean nullable) {
    }
}
