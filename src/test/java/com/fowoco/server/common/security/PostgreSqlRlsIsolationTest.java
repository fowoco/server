package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.List;
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
    private static final UUID USER_A =
            UUID.fromString("a3000000-0000-0000-0000-000000000001");
    private static final UUID USER_B =
            UUID.fromString("b3000000-0000-0000-0000-000000000002");
    private static final UUID TASK_A =
            UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID TASK_B =
            UUID.fromString("b4000000-0000-0000-0000-000000000002");
    private static final UUID STORED_FILE_A =
            UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID STORED_FILE_B =
            UUID.fromString("b5000000-0000-0000-0000-000000000002");
    private static final UUID STORED_FILE_B_UNLINKED =
            UUID.fromString("b5000000-0000-0000-0000-000000000003");
    private static final UUID DRAFT_A =
            UUID.fromString("a6000000-0000-0000-0000-000000000001");
    private static final UUID DRAFT_B =
            UUID.fromString("b6000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_LINK_A =
            UUID.fromString("a7000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_LINK_B =
            UUID.fromString("b7000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_RESPONSE_A =
            UUID.fromString("a8000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_RESPONSE_B =
            UUID.fromString("b8000000-0000-0000-0000-000000000002");
    private static final UUID CASE_A =
            UUID.fromString("a7000000-0000-0000-0000-000000000001");
    private static final UUID CASE_B =
            UUID.fromString("b7000000-0000-0000-0000-000000000002");
    private static final UUID CASE_A_NEW =
            UUID.fromString("a7000000-0000-0000-0000-000000000003");
    private static final UUID CONSENT_A =
            UUID.fromString("a9000000-0000-0000-0000-000000000001");
    private static final UUID CONSENT_B =
            UUID.fromString("b9000000-0000-0000-0000-000000000002");
    private static final UUID PASSWORD_RESET_A =
            UUID.fromString("aa000000-0000-0000-0000-000000000001");
    private static final UUID PASSWORD_RESET_B =
            UUID.fromString("ba000000-0000-0000-0000-000000000002");
    private static final List<String> RLS_TABLES = List.of(
            "company",
            "user_account",
            "worker",
            "task",
            "stored_file",
            "workflow_case",
            "document_request_draft",
            "document_request_draft_type",
            "worker_link",
            "worker_response",
            "worker_response_upload",
            "worker_document_upload_idempotency",
            "user_agreement_consent",
            "password_reset_token"
    );

    @Test
    void restrictedRoleEnforcesTenantCrudAndFailsClosedWithoutValidContext()
            throws SQLException {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String migrationUsername = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String migrationPassword = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");
        String runtimeRole = "rls_isolation_test_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String runtimePassword = "Rls-isolation-" + UUID.randomUUID();

        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                migrationUsername,
                migrationPassword
        )) {
            Flyway.configure()
                    .dataSource(url, migrationUsername, migrationPassword)
                    .locations(
                            "classpath:db/migration",
                            "classpath:db/migration-postgresql"
                    )
                    .load()
                    .migrate();

            try (Connection migrationConnection = DriverManager.getConnection(
                    url,
                    migrationUsername,
                    migrationPassword
            ); PostgreSqlRlsStateFixture rlsState = PostgreSqlRlsStateFixture.capture(
                    migrationConnection,
                    RLS_TABLES
            ); FixtureCleanup fixtureCleanup = () -> restoreFixture(
                    migrationConnection,
                    runtimeRole
            )) {
                rlsState.disableRowLevelSecurityForFixtureSetup();
                prepareFixture(
                        migrationConnection,
                        runtimeRole,
                        runtimePassword,
                        rlsState
                );
                try (Connection runtimeConnection = DriverManager.getConnection(
                        url,
                        runtimeRole,
                        runtimePassword
                )) {
                    assertMissingAndInvalidContextFailClosed(runtimeConnection);
                    assertTenantCrudIsolation(runtimeConnection);
                    assertCommittedContextDoesNotLeak(runtimeConnection);
                }
            }
        }
    }

    private void prepareFixture(
            Connection connection,
            String runtimeRole,
            String runtimePassword,
            PostgreSqlRlsStateFixture rlsState
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
                            + "ON TABLE public.company, public.worker, "
                            + "public.stored_file, public.document_request_draft, "
                            + "public.document_request_draft_type, public.workflow_case, "
                            + "public.worker_link, public.worker_response, "
                            + "public.worker_response_upload, "
                            + "public.worker_document_upload_idempotency, "
                            + "public.user_agreement_consent, "
                            + "public.password_reset_token TO "
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
            statement.execute("""
                    INSERT INTO user_account (
                        user_id, company_id, email, normalized_email,
                        password_hash, role, status
                    ) VALUES
                        ('%s', '%s', 'rls-a@example.com', 'rls-a@example.com',
                         'test-password-hash-a', 'ADMIN', 'ACTIVE'),
                        ('%s', '%s', 'rls-b@example.com', 'rls-b@example.com',
                         'test-password-hash-b', 'ADMIN', 'ACTIVE')
                    """.formatted(USER_A, COMPANY_A, USER_B, COMPANY_B));
            statement.execute("""
                    INSERT INTO user_agreement_consent (
                        consent_id, company_id, user_id, agreement_type,
                        policy_version, agreed, request_id, recorded_at
                    ) VALUES
                        ('%s', '%s', '%s', 'PRIVACY_POLICY', '1.0', TRUE,
                         'rls-consent-a', CURRENT_TIMESTAMP),
                        ('%s', '%s', '%s', 'PRIVACY_POLICY', '1.0', TRUE,
                         'rls-consent-b', CURRENT_TIMESTAMP)
                    """.formatted(
                    CONSENT_A, COMPANY_A, USER_A,
                    CONSENT_B, COMPANY_B, USER_B
            ));
            statement.execute("""
                    INSERT INTO password_reset_token (
                        password_reset_token_id, company_id, user_id, token_hash,
                        expires_at, created_at, updated_at
                    ) VALUES
                        ('%s', '%s', '%s', repeat('e', 64),
                         CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('%s', '%s', '%s', repeat('f', 64),
                         CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(
                    PASSWORD_RESET_A, COMPANY_A, USER_A,
                    PASSWORD_RESET_B, COMPANY_B, USER_B
            ));
            statement.execute("""
                    INSERT INTO workflow_case (
                        case_id, company_id, worker_id, title, lifecycle_status,
                        priority, workflow_catalog_version, workflow_snapshot_json,
                        created_by, created_at, updated_at
                    ) VALUES
                        ('%s', '%s', '%s', 'RLS Case A', 'ACTIVE', 'NORMAL',
                         '2026.07', '{}', '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('%s', '%s', '%s', 'RLS Case B', 'ACTIVE', 'NORMAL',
                         '2026.07', '{}', '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(
                    CASE_A, COMPANY_A, WORKER_A, USER_A,
                    CASE_B, COMPANY_B, WORKER_B, USER_B
            ));
            statement.execute("""
                    INSERT INTO task (
                        task_id, company_id, worker_id, case_id, task_type,
                        workflow_id, workflow_catalog_version, title,
                        business_data_json, critical_fingerprint, content_revision,
                        source, status, created_by, updated_by, created_at, updated_at
                    ) VALUES
                        ('%s', '%s', '%s', 'a4100000-0000-0000-0000-000000000001',
                         'RECONTRACT', 'e9-recontract', '2026.07', 'RLS Task A',
                         '{}', repeat('a', 64), 0, 'MANUAL', 'DRAFT',
                         '%s', '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                        ('%s', '%s', '%s', 'b4100000-0000-0000-0000-000000000002',
                         'RECONTRACT', 'e9-recontract', '2026.07', 'RLS Task B',
                         '{}', repeat('b', 64), 0, 'MANUAL', 'DRAFT',
                         '%s', '%s', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(
                    TASK_A, COMPANY_A, WORKER_A, USER_A, USER_A,
                    TASK_B, COMPANY_B, WORKER_B, USER_B, USER_B
            ));
            statement.execute("""
                    INSERT INTO stored_file (
                        stored_file_id, company_id, name, mime_type, size, purpose,
                        storage_key, scan_status
                    ) VALUES
                        ('%s', '%s', 'tenant-a.pdf', 'application/pdf', 1,
                         'RLS_TEST', 'rls-tenant-a', 'NOT_SCANNED'),
                        ('%s', '%s', 'tenant-b.pdf', 'application/pdf', 1,
                         'RLS_TEST', 'rls-tenant-b', 'NOT_SCANNED'),
                        ('%s', '%s', 'tenant-b-unlinked.pdf', 'application/pdf', 1,
                         'RLS_TEST', 'rls-tenant-b-unlinked', 'NOT_SCANNED')
                    """.formatted(
                    STORED_FILE_A, COMPANY_A,
                    STORED_FILE_B, COMPANY_B,
                    STORED_FILE_B_UNLINKED, COMPANY_B
            ));
            statement.execute("""
                    INSERT INTO document_request_draft (
                        draft_id, task_id, company_id, language, message, review_status
                    ) VALUES
                        ('%s', '%s', '%s', 'ko', 'Tenant A draft', 'DRAFT'),
                        ('%s', '%s', '%s', 'ko', 'Tenant B draft', 'DRAFT')
                    """.formatted(DRAFT_A, TASK_A, COMPANY_A, DRAFT_B, TASK_B, COMPANY_B));
            statement.execute("""
                    INSERT INTO document_request_draft_type (draft_id, document_type)
                    VALUES
                        ('%s', 'PASSPORT_COPY'),
                        ('%s', 'ARC')
                    """.formatted(DRAFT_A, DRAFT_B));
            statement.execute("""
                    INSERT INTO worker_link (
                        worker_link_id, task_id, company_id, token_hash, expires_at,
                        status, conversation_status, issued_by, idempotency_key
                    ) VALUES
                        ('%s', '%s', '%s', repeat('c', 64),
                         CURRENT_TIMESTAMP + INTERVAL '1 day', 'ACTIVE',
                         'WAITING_WORKER', '%s', 'rls-link-a'),
                        ('%s', '%s', '%s', repeat('d', 64),
                         CURRENT_TIMESTAMP + INTERVAL '1 day', 'ACTIVE',
                         'WAITING_WORKER', '%s', 'rls-link-b')
                    """.formatted(
                    WORKER_LINK_A, TASK_A, COMPANY_A, USER_A,
                    WORKER_LINK_B, TASK_B, COMPANY_B, USER_B
            ));
            statement.execute("""
                    INSERT INTO worker_response (
                        response_id, worker_link_id, company_id,
                        response_type, idempotency_key
                    ) VALUES
                        ('%s', '%s', '%s', 'DOCUMENT_SUBMITTED', 'rls-response-a'),
                        ('%s', '%s', '%s', 'DOCUMENT_SUBMITTED', 'rls-response-b')
                    """.formatted(
                    WORKER_RESPONSE_A, WORKER_LINK_A, COMPANY_A,
                    WORKER_RESPONSE_B, WORKER_LINK_B, COMPANY_B
            ));
            statement.execute("""
                    INSERT INTO worker_response_upload (
                        response_id, stored_file_id, company_id
                    ) VALUES
                        ('%s', '%s', '%s'),
                        ('%s', '%s', '%s')
                    """.formatted(
                    WORKER_RESPONSE_A, STORED_FILE_A, COMPANY_A,
                    WORKER_RESPONSE_B, STORED_FILE_B, COMPANY_B
            ));
            statement.execute("""
                    INSERT INTO worker_document_upload_idempotency (
                        worker_link_id, company_id, client_request_id, stored_file_id
                    ) VALUES
                        ('%s', '%s', 'rls-upload-a', '%s'),
                        ('%s', '%s', 'rls-upload-b', '%s')
                    """.formatted(
                    WORKER_LINK_A, COMPANY_A, STORED_FILE_A,
                    WORKER_LINK_B, COMPANY_B, STORED_FILE_B
            ));

        }
        rlsState.enableRowLevelSecurity();
    }

    private void assertMissingAndInvalidContextFailClosed(Connection connection)
            throws SQLException {
        connection.setAutoCommit(false);
        try {
            assertThat(workerCount(connection)).isZero();
            assertThat(tableCount(connection, "stored_file")).isZero();
            assertThat(tableCount(connection, "document_request_draft")).isZero();
            assertThat(tableCount(connection, "document_request_draft_type")).isZero();
            assertThat(tableCount(connection, "worker_link")).isZero();
            assertThat(tableCount(connection, "worker_response")).isZero();
            assertThat(tableCount(connection, "worker_response_upload")).isZero();
            assertThat(tableCount(connection, "worker_document_upload_idempotency")).isZero();
            assertThat(tableCount(connection, "workflow_case")).isZero();
            assertThat(tableCount(connection, "user_agreement_consent")).isZero();
            assertThat(tableCount(connection, "password_reset_token")).isZero();

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
            assertThat(uuidValues(
                    connection,
                    "SELECT stored_file_id FROM public.stored_file ORDER BY stored_file_id"
            )).containsExactly(STORED_FILE_A);
            assertThat(uuidValues(
                    connection,
                    "SELECT draft_id FROM public.document_request_draft ORDER BY draft_id"
            )).containsExactly(DRAFT_A);
            assertThat(stringValues(
                    connection,
                    "SELECT document_type FROM public.document_request_draft_type "
                            + "ORDER BY document_type"
            )).containsExactly("PASSPORT_COPY");

            assertThat(uuidValues(
                    connection,
                    "SELECT worker_link_id FROM public.worker_link ORDER BY worker_link_id"
            )).containsExactly(WORKER_LINK_A);

            assertThat(uuidValues(
                    connection,
                    "SELECT response_id FROM public.worker_response ORDER BY response_id"
            )).containsExactly(WORKER_RESPONSE_A);

            assertThat(uuidValues(
                    connection,
                    "SELECT stored_file_id FROM public.worker_response_upload "
                            + "ORDER BY stored_file_id"
            )).containsExactly(STORED_FILE_A);

            assertThat(uuidValues(
                    connection,
                    "SELECT stored_file_id FROM public.worker_document_upload_idempotency "
                            + "ORDER BY stored_file_id"
            )).containsExactly(STORED_FILE_A);

            assertThat(uuidValues(
                    connection,
                    "SELECT case_id FROM public.workflow_case ORDER BY case_id"
            )).containsExactly(CASE_A);
            assertThat(uuidValues(
                    connection,
                    "SELECT consent_id FROM public.user_agreement_consent ORDER BY consent_id"
            )).containsExactly(CONSENT_A);
            assertThat(uuidValues(
                    connection,
                    "SELECT password_reset_token_id FROM public.password_reset_token "
                            + "ORDER BY password_reset_token_id"
            )).containsExactly(PASSWORD_RESET_A);

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
                    """
                    INSERT INTO workflow_case (
                        case_id, company_id, worker_id, title, lifecycle_status,
                        priority, workflow_catalog_version, workflow_snapshot_json,
                        created_by, created_at, updated_at
                    ) VALUES (?, ?, ?, 'RLS Case A New', 'ACTIVE', 'NORMAL',
                              '2026.07', '{}', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    CASE_A_NEW,
                    COMPANY_A,
                    WORKER_A,
                    USER_A
            )).isOne();
            assertThat(executeUpdate(
                    connection,
                    "UPDATE workflow_case SET title = 'RLS Case A Updated' WHERE case_id = ?",
                    CASE_A_NEW
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
                    INSERT INTO workflow_case (
                        case_id, company_id, worker_id, title, lifecycle_status,
                        priority, workflow_catalog_version, workflow_snapshot_json,
                        created_by, created_at, updated_at
                    ) VALUES (
                        'b7000000-0000-0000-0000-000000000099', '%s', '%s',
                        'Forbidden Case B', 'ACTIVE', 'NORMAL', '2026.07', '{}', '%s',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(COMPANY_B, WORKER_B, USER_B)
            );
            assertSqlState(
                    connection,
                    "42501",
                    """
                    UPDATE workflow_case
                       SET company_id = '%s', worker_id = '%s', created_by = '%s'
                     WHERE case_id = '%s'
                    """.formatted(COMPANY_B, WORKER_B, USER_B, CASE_A_NEW)
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
            assertSqlState(
                    connection,
                    "42501",
                    """
                    INSERT INTO stored_file (
                        stored_file_id, company_id, name, mime_type, size, purpose,
                        storage_key, scan_status
                    ) VALUES (
                        'b5000000-0000-0000-0000-000000000099',
                        '%s', 'forbidden.pdf', 'application/pdf', 1,
                        'RLS_TEST', 'rls-forbidden-b', 'NOT_SCANNED'
                    )
                    """.formatted(COMPANY_B)
            );
            assertSqlState(
                    connection,
                    "42501",
                    """
                    INSERT INTO document_request_draft_type (draft_id, document_type)
                    VALUES ('%s', 'CONTRACT')
                    """.formatted(DRAFT_B)
            );
            assertSqlState(
                    connection,
                    "42501",
                    """
                    INSERT INTO worker_document_upload_idempotency (
                        worker_link_id, company_id, client_request_id, stored_file_id
                    ) VALUES (
                        '%s', '%s', 'forbidden-rls-upload-b', '%s'
                    )
                    """.formatted(WORKER_LINK_B, COMPANY_B, STORED_FILE_B)
            );
            assertSqlState(
                    connection,
                    "42501",
                    """
                    INSERT INTO password_reset_token (
                        password_reset_token_id, company_id, user_id, token_hash,
                        expires_at, created_at, updated_at
                    ) VALUES (
                        'bb000000-0000-0000-0000-000000000099', '%s', '%s', repeat('9', 64),
                        CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.formatted(COMPANY_B, USER_B)
            );
            assertSqlState(
                    connection,
                    "42501",
                    """
                    INSERT INTO worker_response_upload (
                        response_id, stored_file_id, company_id
                    ) VALUES (
                        '%s', '%s', '%s'
                    )
                    """.formatted(
                    WORKER_RESPONSE_B,
                    STORED_FILE_B_UNLINKED,
                    COMPANY_B
            ));

            assertThat(executeUpdate(
                    connection,
                    "UPDATE worker SET display_name = 'Hidden Update' WHERE worker_id = ?",
                    WORKER_B
            )).isZero();
            assertThat(executeUpdate(
                    connection,
                    "UPDATE workflow_case SET title = 'Hidden Update' WHERE case_id = ?",
                    CASE_B
            )).isZero();
            assertThat(executeUpdate(
                    connection,
                    "DELETE FROM workflow_case WHERE case_id = ?",
                    CASE_B
            )).isZero();
            assertThat(executeUpdate(
                    connection,
                    "DELETE FROM workflow_case WHERE case_id = ?",
                    CASE_A_NEW
            )).isOne();
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
            assertThat(executeUpdate(
                    connection,
                    "UPDATE password_reset_token SET used_at = CURRENT_TIMESTAMP "
                            + "WHERE password_reset_token_id = ?",
                    PASSWORD_RESET_B
            )).isZero();
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
        assertThat(tableCount(connection, "workflow_case")).isZero();
        setTenantContext(connection, COMPANY_B.toString());
        assertThat(workerIds(connection)).containsExactly(WORKER_B);
        assertThat(uuidValues(
                connection,
                "SELECT case_id FROM public.workflow_case ORDER BY case_id"
        )).containsExactly(CASE_B);
        connection.rollback();
    }

    private void restoreFixture(Connection connection, String runtimeRole) throws SQLException {
        SQLException failure = null;
        failure = runCleanupStep(failure, () -> {
            try (Statement statement = connection.createStatement()) {
                deleteFixtureRows(statement);
            }
        });
        failure = runCleanupStep(failure, () -> {
            try (Statement statement = connection.createStatement()) {
                if (roleExists(statement, runtimeRole)) {
                    statement.execute("DROP OWNED BY " + quoteIdentifier(runtimeRole));
                }
            }
        });
        failure = runCleanupStep(failure, () -> {
            try (Statement statement = connection.createStatement()) {
                if (roleExists(statement, runtimeRole)) {
                    statement.execute("DROP ROLE " + quoteIdentifier(runtimeRole));
                }
            }
        });
        if (failure != null) {
            throw failure;
        }
    }

    private SQLException runCleanupStep(SQLException failure, SqlCleanupStep step) {
        try {
            step.run();
        } catch (SQLException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private void deleteFixtureRows(Statement statement) throws SQLException {
        statement.execute("""
                DELETE FROM password_reset_token
                WHERE password_reset_token_id IN ('%s', '%s')
                """.formatted(PASSWORD_RESET_A, PASSWORD_RESET_B));
        statement.execute("""
                DELETE FROM user_agreement_consent
                WHERE consent_id IN ('%s', '%s')
                """.formatted(CONSENT_A, CONSENT_B));
        statement.execute("""
                DELETE FROM worker_document_upload_idempotency
                WHERE worker_link_id IN ('%s', '%s')
                """.formatted(WORKER_LINK_A, WORKER_LINK_B));
        statement.execute("""
                DELETE FROM worker_response_upload
                WHERE response_id IN ('%s', '%s')
                """.formatted(WORKER_RESPONSE_A, WORKER_RESPONSE_B));
        statement.execute("""
                DELETE FROM worker_response
                WHERE response_id IN ('%s', '%s')
                """.formatted(WORKER_RESPONSE_A, WORKER_RESPONSE_B));
        statement.execute("""
                DELETE FROM worker_link
                WHERE worker_link_id IN ('%s', '%s')
                """.formatted(WORKER_LINK_A, WORKER_LINK_B));
        statement.execute("""
                DELETE FROM document_request_draft_type
                WHERE draft_id IN ('%s', '%s')
                """.formatted(DRAFT_A, DRAFT_B));
        statement.execute("""
                DELETE FROM document_request_draft
                WHERE draft_id IN ('%s', '%s')
                """.formatted(DRAFT_A, DRAFT_B));
        statement.execute("""
                DELETE FROM stored_file
                WHERE stored_file_id IN ('%s', '%s', '%s')
                   OR storage_key = 'rls-forbidden-b'
                """.formatted(STORED_FILE_A, STORED_FILE_B, STORED_FILE_B_UNLINKED));
        statement.execute("""
                DELETE FROM task
                WHERE task_id IN ('%s', '%s')
                """.formatted(TASK_A, TASK_B));
        statement.execute("""
                DELETE FROM workflow_case
                WHERE case_id IN ('%s', '%s', '%s')
                """.formatted(CASE_A, CASE_B, CASE_A_NEW));
        statement.execute("""
                DELETE FROM worker
                WHERE worker_id IN (
                    '%s', '%s', '%s', '%s'
                )
                """.formatted(WORKER_A, WORKER_B, WORKER_A_NEW, WORKER_B_NEW));
        statement.execute("""
                DELETE FROM user_account
                WHERE user_id IN ('%s', '%s')
                """.formatted(USER_A, USER_B));
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

    private int tableCount(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM public." + tableName
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

    private java.util.List<UUID> uuidValues(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            java.util.List<UUID> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getObject(1, UUID.class));
            }
            return java.util.List.copyOf(values);
        }
    }

    private java.util.List<String> stringValues(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            java.util.List<String> values = new java.util.ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return java.util.List.copyOf(values);
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

    @FunctionalInterface
    private interface FixtureCleanup extends AutoCloseable {

        @Override
        void close() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlCleanupStep {

        void run() throws SQLException;
    }
}
