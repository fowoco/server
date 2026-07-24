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
    private static final String WORKER_A = "12000000-0000-0000-0000-000000000001";
    private static final String TASK_A = "13000000-0000-0000-0000-000000000001";
    private static final String EVENT_A = "18000000-0000-0000-0000-000000000001";
    private static final String TOKEN_HASH_A = "a".repeat(64);

    @Test
    void migrationsApplyCanonicalServerSchemaOnPostgreSql() throws SQLException {
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
                .contains(
                        "company",
                        "user_account",
                        "refresh_token",
                        "worker",
                        "worker_document",
                        "task",
                        "task_checklist_item",
                        "task_transition_history",
                        "approval_request",
                        "external_submission",
                        "task_evidence",
                        "audit_event",
                        "event_publication",
                        "event_consumption"
                );

        assertThat(columnSpecs(connection, "company"))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("status", new ColumnSpec("varchar", false))
                .containsEntry("version", new ColumnSpec("int8", false))
                .doesNotContainKey("company_name");
        assertThat(columnSpecs(connection, "user_account"))
                .containsEntry("user_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("display_name", new ColumnSpec("varchar", false))
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
        assertThat(columnSpecs(connection, "worker"))
                .containsEntry("worker_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("nationality_code", new ColumnSpec("varchar", true))
                .containsEntry("work_status", new ColumnSpec("varchar", false))
                .containsEntry("stay_expiry_date", new ColumnSpec("date", true))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "worker_document"))
                .containsEntry("worker_document_id", new ColumnSpec("uuid", false))
                .containsEntry("worker_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("document_type", new ColumnSpec("varchar", false))
                .containsEntry("submission_status", new ColumnSpec("varchar", false))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "task"))
                .containsEntry("task_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("worker_id", new ColumnSpec("uuid", false))
                .containsEntry("content_revision", new ColumnSpec("int8", false))
                .containsEntry("critical_fingerprint", new ColumnSpec("varchar", false))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "approval_request"))
                .containsEntry("approval_request_id", new ColumnSpec("uuid", false))
                .containsEntry("task_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("target_task_version", new ColumnSpec("int8", false))
                .containsEntry("target_content_revision", new ColumnSpec("int8", false))
                .containsEntry("target_fingerprint", new ColumnSpec("varchar", false))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "audit_event"))
                .containsEntry("audit_event_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("request_id", new ColumnSpec("varchar", false))
                .containsEntry("trace_id", new ColumnSpec("varchar", true));
        assertThat(columnSpecs(connection, "event_publication"))
                .containsEntry("event_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("event_type", new ColumnSpec("varchar", false))
                .containsEntry("payload_json", new ColumnSpec("text", false))
                .containsEntry("status", new ColumnSpec("varchar", false))
                .containsEntry("attempt_count", new ColumnSpec("int4", false))
                .containsEntry("next_attempt_at", new ColumnSpec("timestamptz", true))
                .containsEntry("lease_expires_at", new ColumnSpec("timestamptz", true))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "event_consumption"))
                .containsEntry("consumption_id", new ColumnSpec("uuid", false))
                .containsEntry("event_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("handler_name", new ColumnSpec("varchar", false))
                .containsEntry("completed_at", new ColumnSpec("timestamptz", false));

        assertThat(constraintNames(connection))
                .contains(
                        "pk_company",
                        "pk_user_account",
                        "fk_user_account_company",
                        "uq_user_account_normalized_email",
                        "uq_user_account_user_company",
                        "pk_refresh_token",
                        "uq_refresh_token_hash",
                        "fk_refresh_token_user_company",
                        "fk_worker_company",
                        "fk_worker_document_worker",
                        "fk_task_worker_company",
                        "fk_task_created_by_company",
                        "fk_approval_request_task_company",
                        "fk_approval_request_requester_company",
                        "fk_audit_event_company",
                        "pk_event_publication",
                        "uq_event_publication_id_company",
                        "fk_event_publication_company",
                        "pk_event_consumption",
                        "uq_event_consumption_event_handler",
                        "fk_event_consumption_publication"
                );
        assertThat(indexNames(connection))
                .contains(
                        "idx_user_account_company",
                        "idx_refresh_token_company_user",
                        "idx_refresh_token_family_revoked",
                        "idx_refresh_token_expires_at",
                        "idx_worker_company",
                        "idx_worker_document_company_status",
                        "idx_task_company_status_due",
                        "idx_approval_request_task_status",
                        "idx_audit_event_company_time",
                        "idx_event_publication_claim",
                        "idx_event_publication_company_time",
                        "idx_event_consumption_company_event"
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
        execute(connection, """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality_code,
                    preferred_language, work_status, stay_expiry_date
                ) VALUES (
                    '%s', '%s', 'Worker A', 'VNM', 'vi', 'ACTIVE', CURRENT_DATE + 30
                )
                """.formatted(WORKER_A, COMPANY_A));
        execute(connection, """
                INSERT INTO task (
                    task_id, company_id, worker_id, case_id, task_type,
                    workflow_id, workflow_catalog_version, title,
                    business_data_json, critical_fingerprint, content_revision,
                    source, status, created_by, updated_by, created_at, updated_at
                ) VALUES (
                    '%s', '%s', '%s', '14000000-0000-0000-0000-000000000001',
                    'RECONTRACT', 'e9-recontract', '2026.07', 'Recontract',
                    '{}', '%s', 0, 'MANUAL', 'DRAFT', '%s', '%s',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(TASK_A, COMPANY_A, WORKER_A, "f".repeat(64), USER_A, USER_A));
        execute(connection, """
                INSERT INTO approval_request (
                    approval_request_id, task_id, company_id,
                    target_task_version, target_content_revision, target_fingerprint,
                    status, hr_snapshot_json, changed_fields_json, source_versions_json,
                    requested_by, requested_at, created_at, updated_at
                ) VALUES (
                    '15000000-0000-0000-0000-000000000001',
                    '%s', '%s', 0, 0, '%s', 'PENDING',
                    '{}', '[]', '{}', '%s',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(TASK_A, COMPANY_A, "f".repeat(64), USER_A));
        execute(connection, """
                INSERT INTO event_publication (
                    event_id, company_id, event_type, payload_version,
                    aggregate_type, aggregate_id, actor_type, request_id,
                    payload_json, status, attempt_count, next_attempt_at,
                    occurred_at, created_at, updated_at
                ) VALUES (
                    '%s', '%s', 'TaskCreated', '1',
                    'Task', '%s', 'SYSTEM_RULE', 'migration-test-request',
                    '{}', 'PENDING', 0, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(EVENT_A, COMPANY_A, TASK_A));
        execute(connection, """
                INSERT INTO event_consumption (
                    consumption_id, event_id, company_id, handler_name, completed_at
                ) VALUES (
                    '19000000-0000-0000-0000-000000000001',
                    '%s', '%s', 'migration-test-handler', CURRENT_TIMESTAMP
                )
                """.formatted(EVENT_A, COMPANY_A));

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
        assertSqlState(connection, "23503", """
                INSERT INTO approval_request (
                    approval_request_id, task_id, company_id,
                    target_task_version, target_content_revision, target_fingerprint,
                    status, hr_snapshot_json, changed_fields_json, source_versions_json,
                    requested_by, requested_at, created_at, updated_at
                ) VALUES (
                    '16000000-0000-0000-0000-000000000001',
                    '%s', '%s', 0, 0, '%s', 'PENDING',
                    '{}', '[]', '{}', '%s',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(TASK_A, COMPANY_A, "f".repeat(64), USER_B));
        assertSqlState(connection, "23514", """
                INSERT INTO audit_event (
                    audit_event_id, company_id, actor_type, actor_id, user_role,
                    action, target_type, target_id, request_id,
                    event_version, change_summary, created_at
                ) VALUES (
                    '17000000-0000-0000-0000-000000000001',
                    '%s', 'HR_USER', '%s', 'ADMIN',
                    'TASK_APPROVED', 'TASK', '%s', '',
                    '1', 'approved', CURRENT_TIMESTAMP
                )
                """.formatted(COMPANY_A, USER_A, TASK_A));
        assertSqlState(connection, "23503", """
                INSERT INTO event_consumption (
                    consumption_id, event_id, company_id, handler_name, completed_at
                ) VALUES (
                    '19000000-0000-0000-0000-000000000002',
                    '%s', '%s', 'wrong-tenant-handler', CURRENT_TIMESTAMP
                )
                """.formatted(EVENT_A, COMPANY_B));
        assertSqlState(connection, "23505", """
                INSERT INTO event_consumption (
                    consumption_id, event_id, company_id, handler_name, completed_at
                ) VALUES (
                    '19000000-0000-0000-0000-000000000003',
                    '%s', '%s', 'migration-test-handler', CURRENT_TIMESTAMP
                )
                """.formatted(EVENT_A, COMPANY_A));
        assertSqlState(connection, "23514", """
                INSERT INTO event_publication (
                    event_id, company_id, event_type, payload_version,
                    aggregate_type, aggregate_id, actor_type, request_id,
                    payload_json, status, attempt_count,
                    occurred_at, created_at, updated_at
                ) VALUES (
                    '18000000-0000-0000-0000-000000000002',
                    '%s', 'TaskCreated', '1', 'Task', '%s',
                    'SYSTEM_RULE', 'invalid-state-request', '{}',
                    'UNKNOWN', 0,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(COMPANY_A, TASK_A));
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
