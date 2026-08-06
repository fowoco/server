package com.fowoco.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
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
    private static final String PASSWORD_RESET_TOKEN_HASH_A = "e".repeat(64);
    private static final String ACTIVE_WORKER_LINK_TOKEN_HASH = "b".repeat(64);
    private static final String REVOKED_WORKER_LINK_TOKEN_HASH = "c".repeat(64);
    private static final String EXPIRED_WORKER_LINK_TOKEN_HASH = "d".repeat(64);

    @Test
    void migrationsApplyCanonicalServerSchemaOnPostgreSql() throws SQLException {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");
        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                username,
                password
        )) {
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
    }

    private void assertSchemaContract(Connection connection) throws SQLException {
        assertThat(tableNames(connection))
                .contains(
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
                        "document_request_draft_type",
                        "ai_run",
                        "ai_attempt",
                        "ai_question",
                        "ai_candidate",
                        "ai_candidate_decision_batch",
                        "ai_candidate_decision",
                        "ai_candidate_decision_task",
                        "workflow_case",
                        "worker_link",
                        "worker_response",
                        "worker_response_upload",
                        "worker_document_upload_idempotency",
                        "user_agreement_consent",
                        "password_reset_token"
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
        assertThat(columnSpecs(connection, "user_agreement_consent"))
                .containsEntry("consent_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("user_id", new ColumnSpec("uuid", false))
                .containsEntry("agreement_type", new ColumnSpec("varchar", false))
                .containsEntry("policy_version", new ColumnSpec("varchar", false))
                .containsEntry("agreed", new ColumnSpec("bool", false))
                .containsEntry("request_id", new ColumnSpec("varchar", false));
        assertThat(columnSpecs(connection, "password_reset_token"))
                .containsEntry("password_reset_token_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("user_id", new ColumnSpec("uuid", false))
                .containsEntry("token_hash", new ColumnSpec("varchar", false))
                .containsEntry("expires_at", new ColumnSpec("timestamptz", false))
                .containsEntry("used_at", new ColumnSpec("timestamptz", true))
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
                .containsEntry("task_id", new ColumnSpec("uuid", true))
                .containsEntry("document_type", new ColumnSpec("varchar", false))
                .containsEntry("submission_status", new ColumnSpec("varchar", false))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "stored_file"))
                .containsEntry("stored_file_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("task_id", new ColumnSpec("uuid", true))
                .containsEntry("worker_id", new ColumnSpec("uuid", true))
                .containsEntry("storage_key", new ColumnSpec("varchar", false))
                .containsEntry("scan_status", new ColumnSpec("varchar", false));
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
        assertThat(columnSpecs(connection, "document_request_draft"))
                .containsEntry("draft_id", new ColumnSpec("uuid", false))
                .containsEntry("task_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("review_status", new ColumnSpec("varchar", false))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "document_request_draft_type"))
                .containsEntry("draft_id", new ColumnSpec("uuid", false))
                .containsEntry("document_type", new ColumnSpec("varchar", false))
                .doesNotContainKey("company_id");
        assertThat(columnSpecs(connection, "ai_run"))
                .containsEntry("ai_run_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("instruction_hash", new ColumnSpec("varchar", false))
                .containsEntry("idempotency_key_hash", new ColumnSpec("varchar", false))
                .containsEntry("status", new ColumnSpec("varchar", false))
                .containsEntry("analysis_outcome", new ColumnSpec("varchar", true))
                .containsEntry("version", new ColumnSpec("int8", false));
        assertThat(columnSpecs(connection, "ai_attempt"))
                .containsEntry("ai_attempt_id", new ColumnSpec("uuid", false))
                .containsEntry("ai_run_id", new ColumnSpec("uuid", false))
                .containsEntry("phase", new ColumnSpec("varchar", false))
                .containsEntry("analysis_input_json", new ColumnSpec("text", false))
                .containsEntry("latency_ms", new ColumnSpec("int8", true));
        assertThat(columnSpecs(connection, "ai_question"))
                .containsEntry("ai_question_id", new ColumnSpec("uuid", false))
                .containsEntry("ai_attempt_id", new ColumnSpec("uuid", false))
                .containsEntry("slot_key", new ColumnSpec("varchar", false))
                .containsEntry("answer_value", new ColumnSpec("varchar", true));
        assertThat(columnSpecs(connection, "ai_candidate"))
                .containsEntry("ai_candidate_id", new ColumnSpec("uuid", false))
                .containsEntry("ai_attempt_id", new ColumnSpec("uuid", false))
                .containsEntry("worker_id", new ColumnSpec("uuid", false))
                .containsEntry("confidence", new ColumnSpec("numeric", false));
        assertThat(columnSpecs(connection, "ai_candidate_decision_batch"))
                .containsEntry("decision_batch_id", new ColumnSpec("uuid", false))
                .containsEntry("ai_run_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("case_id", new ColumnSpec("uuid", true))
                .containsEntry("resulting_run_version", new ColumnSpec("int8", true));
        assertThat(columnSpecs(connection, "ai_candidate_decision"))
                .containsEntry("decision_id", new ColumnSpec("uuid", false))
                .containsEntry("ai_candidate_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("action", new ColumnSpec("varchar", false));
        assertThat(columnSpecs(connection, "ai_candidate_decision_task"))
                .containsEntry("decision_id", new ColumnSpec("uuid", false))
                .containsEntry("task_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("sequence_no", new ColumnSpec("int4", false));
        assertThat(columnSpecs(connection, "worker_link"))
                .containsEntry("worker_link_id", new ColumnSpec("uuid", false))
                .containsEntry("task_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("replaces_link_id", new ColumnSpec("uuid", true));
        assertThat(columnSpecs(connection, "worker_response"))
                .containsEntry("response_id", new ColumnSpec("uuid", false))
                .containsEntry("worker_link_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false));
        assertThat(columnSpecs(connection, "worker_response_upload"))
                .containsEntry("response_id", new ColumnSpec("uuid", false))
                .containsEntry("stored_file_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false));
        assertThat(columnSpecs(connection, "worker_document_upload_idempotency"))
                .containsEntry("worker_link_id", new ColumnSpec("uuid", false))
                .containsEntry("stored_file_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false));
        assertThat(columnSpecs(connection, "workflow_case"))
                .containsEntry("case_id", new ColumnSpec("uuid", false))
                .containsEntry("company_id", new ColumnSpec("uuid", false))
                .containsEntry("worker_id", new ColumnSpec("uuid", false))
                .containsEntry("lifecycle_status", new ColumnSpec("varchar", false))
                .containsEntry("workflow_snapshot_json", new ColumnSpec("text", false))
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
                        "fk_refresh_token_user_company",
                        "fk_worker_company",
                        "fk_worker_document_worker",
                        "pk_stored_file",
                        "fk_stored_file_company",
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
                        "fk_event_consumption_publication",
                        "pk_document_request_draft",
                        "fk_document_request_draft_task_company",
                        "fk_document_request_draft_type_draft",
                        "pk_ai_run",
                        "uq_ai_run_company_idempotency",
                        "fk_ai_run_requester_company",
                        "pk_ai_attempt",
                        "fk_ai_attempt_run_company",
                        "pk_ai_question",
                        "fk_ai_question_attempt_company",
                        "pk_ai_candidate",
                        "uq_ai_candidate_id_company",
                        "fk_ai_candidate_worker_company",
                        "pk_ai_candidate_decision_batch",
                        "uq_ai_candidate_decision_batch_idempotency",
                        "fk_ai_candidate_decision_batch_run_company",
                        "fk_ai_candidate_decision_batch_case_company",
                        "pk_ai_candidate_decision",
                        "uq_ai_candidate_decision_candidate",
                        "fk_ai_candidate_decision_candidate_company",
                        "pk_ai_candidate_decision_task",
                        "fk_ai_candidate_decision_task_task_company",
                        "pk_workflow_case",
                        "uq_workflow_case_id_company",
                        "fk_workflow_case_worker_company",
                        "fk_workflow_case_created_by_company",
                        "uq_task_id_worker_company",
                        "fk_worker_document_task_worker_company",
                        "uq_worker_link_id_company",
                        "fk_worker_link_replaces_company",
                        "uq_worker_response_id_company",
                        "fk_worker_response_link_company",
                        "uq_stored_file_id_company",
                        "uq_worker_response_upload_file_company",
                        "fk_worker_response_upload_response_company",
                        "fk_worker_response_upload_file_company",
                        "fk_worker_document_upload_idempotency_link_company",
                        "fk_worker_document_upload_idempotency_file_company",
                        "pk_user_agreement_consent",
                        "fk_user_agreement_consent_user_company",
                        "pk_password_reset_token",
                        "uq_password_reset_token_hash",
                        "fk_password_reset_token_user_company"
                );
        assertThat(indexNames(connection))
                .contains(
                        "idx_user_account_company",
                        "idx_refresh_token_company_user",
                        "idx_refresh_token_family_revoked",
                        "idx_refresh_token_expires_at",
                        "idx_worker_company",
                        "idx_worker_document_company_status",
                        "idx_stored_file_company",
                        "idx_task_company_status_due",
                        "idx_approval_request_task_status",
                        "idx_audit_event_company_time",
                        "idx_event_publication_claim",
                        "idx_event_publication_company_time",
                        "idx_event_consumption_company_event",
                        "idx_document_request_draft_company",
                        "idx_ai_run_company_created",
                        "idx_ai_attempt_run",
                        "idx_ai_question_run",
                        "idx_ai_candidate_run",
                        "idx_ai_candidate_decision_batch_run",
                        "idx_ai_candidate_decision_run",
                        "idx_ai_candidate_decision_task_task",
                        "idx_workflow_case_company_updated",
                        "idx_workflow_case_company_worker",
                        "idx_worker_response_upload_company",
                        "idx_worker_document_upload_idempotency_company",
                        "idx_worker_document_upload_idempotency_file_company",
                        "idx_user_agreement_consent_user_time",
                        "idx_password_reset_token_company_user",
                        "idx_password_reset_token_active"
                );
        assertThat(policyNames(connection))
                .containsExactlyInAnyOrder(
                        "pl_company_tenant_isolation",
                        "pl_user_account_tenant_isolation",
                        "pl_refresh_token_tenant_isolation",
                        "pl_worker_tenant_isolation",
                        "pl_worker_document_tenant_isolation",
                        "pl_stored_file_tenant_isolation",
                        "pl_task_tenant_isolation",
                        "pl_task_checklist_item_tenant_isolation",
                        "pl_task_transition_history_tenant_isolation",
                        "pl_approval_request_tenant_isolation",
                        "pl_external_submission_tenant_isolation",
                        "pl_task_evidence_tenant_isolation",
                        "pl_audit_event_tenant_isolation",
                        "pl_event_publication_tenant_isolation",
                        "pl_event_consumption_tenant_isolation",
                        "pl_document_request_draft_tenant_isolation",
                        "pl_document_request_draft_type_tenant_isolation",
                        "pl_ai_run_tenant_isolation",
                        "pl_ai_attempt_tenant_isolation",
                        "pl_ai_question_tenant_isolation",
                        "pl_ai_candidate_tenant_isolation",
                        "pl_ai_candidate_decision_batch_tenant_isolation",
                        "pl_ai_candidate_decision_tenant_isolation",
                        "pl_ai_candidate_decision_task_tenant_isolation",
                        "pl_workflow_case_tenant_isolation",
                        "pl_worker_link_tenant_isolation",
                        "pl_worker_response_tenant_isolation",
                        "pl_worker_response_upload_tenant_isolation",
                        "pl_worker_document_upload_idempotency_tenant_isolation",
                        "pl_user_agreement_consent_tenant_isolation",
                        "pl_password_reset_token_tenant_isolation"
                );
        assertThat(rlsEnabledTables(connection)).isEmpty();
        assertThat(securityDefinerFunctionNames(connection))
                .containsExactlyInAnyOrder(
                        "bootstrap_company_id_by_normalized_email",
                        "bootstrap_company_id_by_refresh_token_hash",
                        "bootstrap_company_id_by_password_reset_token_hash",
                        "bootstrap_company_id_by_worker_link_token_hash",
                        "bootstrap_claim_event_publications",
                        "bootstrap_count_outstanding_event_publications",
                        "bootstrap_oldest_outstanding_event_occurred_at"
                );
        assertThat(functionsWithLockedSearchPath(connection))
                .containsExactlyInAnyOrder(
                        "bootstrap_company_id_by_normalized_email",
                        "bootstrap_company_id_by_refresh_token_hash",
                        "bootstrap_company_id_by_password_reset_token_hash",
                        "bootstrap_company_id_by_worker_link_token_hash",
                        "bootstrap_claim_event_publications",
                        "bootstrap_count_outstanding_event_publications",
                        "bootstrap_oldest_outstanding_event_occurred_at"
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
                INSERT INTO user_agreement_consent (
                    consent_id, company_id, user_id, agreement_type,
                    policy_version, agreed, request_id, recorded_at
                ) VALUES (
                    '33000000-0000-0000-0000-000000000001', '%s', '%s',
                    'PRIVACY_POLICY', '1.0', TRUE, 'migration-test-request', CURRENT_TIMESTAMP
                )
                """.formatted(COMPANY_A, USER_A));
        execute(connection, """
                INSERT INTO password_reset_token (
                    password_reset_token_id, company_id, user_id, token_hash,
                    expires_at, created_at, updated_at
                ) VALUES (
                    '34000000-0000-0000-0000-000000000001', '%s', '%s', '%s',
                    CURRENT_TIMESTAMP + INTERVAL '30 minutes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(COMPANY_A, USER_A, PASSWORD_RESET_TOKEN_HASH_A));
        execute(connection, """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality_code,
                    preferred_language, work_status, stay_expiry_date
                ) VALUES (
                    '%s', '%s', 'Worker A', 'VNM', 'vi', 'ACTIVE', CURRENT_DATE + 30
                )
                """.formatted(WORKER_A, COMPANY_A));
        execute(connection, """
                INSERT INTO workflow_case (
                    case_id, company_id, worker_id, title, lifecycle_status,
                    priority, workflow_catalog_version, workflow_snapshot_json,
                    created_by, created_at, updated_at
                ) VALUES (
                    '14000000-0000-0000-0000-000000000001', '%s', '%s',
                    'Recontract case', 'ACTIVE', 'NORMAL', '2026.07', '{}', '%s',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(COMPANY_A, WORKER_A, USER_A));
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
                INSERT INTO worker_link (
                    worker_link_id, task_id, company_id, token_hash, expires_at,
                    status, conversation_status, issued_by, idempotency_key,
                    created_at, updated_at
                ) VALUES
                    (
                        '21000000-0000-0000-0000-000000000001', '%s', '%s', '%s',
                        CURRENT_TIMESTAMP + INTERVAL '1 day', 'ACTIVE',
                        'WAITING_WORKER', '%s', 'active-link',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    ),
                    (
                        '21000000-0000-0000-0000-000000000002', '%s', '%s', '%s',
                        CURRENT_TIMESTAMP + INTERVAL '1 day', 'REVOKED',
                        'WAITING_WORKER', '%s', 'revoked-link',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    ),
                    (
                        '21000000-0000-0000-0000-000000000003', '%s', '%s', '%s',
                        CURRENT_TIMESTAMP - INTERVAL '1 day', 'ACTIVE',
                        'WAITING_WORKER', '%s', 'expired-link',
                        CURRENT_TIMESTAMP - INTERVAL '2 days',
                        CURRENT_TIMESTAMP - INTERVAL '2 days'
                    )
                """.formatted(
                TASK_A, COMPANY_A, ACTIVE_WORKER_LINK_TOKEN_HASH, USER_A,
                TASK_A, COMPANY_A, REVOKED_WORKER_LINK_TOKEN_HASH, USER_A,
                TASK_A, COMPANY_A, EXPIRED_WORKER_LINK_TOKEN_HASH, USER_A
        ));
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

        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_normalized_email(?)",
                "admin.a@example.com"
        )).isEqualTo(COMPANY_A);
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_normalized_email(?)",
                "missing@example.com"
        )).isNull();
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_refresh_token_hash(?)",
                TOKEN_HASH_A
        )).isEqualTo(COMPANY_A);
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_refresh_token_hash(?)",
                "0".repeat(64)
        )).isNull();
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_password_reset_token_hash(?)",
                PASSWORD_RESET_TOKEN_HASH_A
        )).isEqualTo(COMPANY_A);
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_password_reset_token_hash(?)",
                "0".repeat(64)
        )).isNull();
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_worker_link_token_hash(?)",
                ACTIVE_WORKER_LINK_TOKEN_HASH
        )).isEqualTo(COMPANY_A);
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_worker_link_token_hash(?)",
                REVOKED_WORKER_LINK_TOKEN_HASH
        )).isNull();
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_worker_link_token_hash(?)",
                EXPIRED_WORKER_LINK_TOKEN_HASH
        )).isNull();
        assertThat(queryNullableString(
                connection,
                "SELECT public.bootstrap_company_id_by_worker_link_token_hash(?)",
                "0".repeat(64)
        )).isNull();

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
                INSERT INTO workflow_case (
                    case_id, company_id, worker_id, title, lifecycle_status,
                    priority, workflow_catalog_version, workflow_snapshot_json,
                    created_by, created_at, updated_at
                ) VALUES (
                    '14000000-0000-0000-0000-000000000002', '%s', '%s',
                    'Wrong tenant case', 'ACTIVE', 'NORMAL', '2026.07', '{}', '%s',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """.formatted(COMPANY_B, WORKER_A, USER_B));
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

    private Set<String> policyNames(Connection connection) throws SQLException {
        return queryStrings(
                connection,
                """
                SELECT policyname
                FROM pg_catalog.pg_policies
                WHERE schemaname = 'public'
                """
        );
    }

    private Set<String> rlsEnabledTables(Connection connection) throws SQLException {
        return queryStrings(
                connection,
                """
                SELECT relname
                FROM pg_catalog.pg_class
                WHERE relnamespace = 'public'::regnamespace
                  AND relkind = 'r'
                  AND relrowsecurity
                """
        );
    }

    private Set<String> securityDefinerFunctionNames(Connection connection) throws SQLException {
        return queryStrings(
                connection,
                """
                SELECT routine.routine_name
                FROM information_schema.routines AS routine
                WHERE routine.routine_schema = 'public'
                  AND routine.security_type = 'DEFINER'
                  AND routine.routine_name LIKE 'bootstrap_%'
                """
        );
    }

    private Set<String> functionsWithLockedSearchPath(Connection connection) throws SQLException {
        return queryStrings(
                connection,
                """
                SELECT procedure.proname
                FROM pg_catalog.pg_proc AS procedure
                WHERE procedure.pronamespace = 'public'::regnamespace
                  AND procedure.proname LIKE 'bootstrap_%'
                  AND 'search_path=pg_catalog, public, pg_temp' =
                      ANY(procedure.proconfig)
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

    private String queryNullableString(Connection connection, String sql, String parameter)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
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
