package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

final class PostgreSqlRestrictedRoleHttpDataFixture {

    static final String PASSWORD = "Restricted-role-test-1!";
    static final String USER_A_EMAIL = "rls.http.a@example.test";
    static final String USER_B_EMAIL = "rls.http.b@example.test";
    static final String ACTIVE_WORKER_LINK_TOKEN = "rls-http-active-worker-link-token";
    static final String EXPIRED_WORKER_LINK_TOKEN = "rls-http-expired-worker-link-token";
    static final String REVOKED_WORKER_LINK_TOKEN = "rls-http-revoked-worker-link-token";
    static final String EXPIRED_REFRESH_TOKEN = "e".repeat(43);
    static final String REVOKED_REFRESH_TOKEN = "r".repeat(43);

    private static final UUID COMPANY_A =
            UUID.fromString("a9800000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("b9800000-0000-0000-0000-000000000002");
    private static final UUID USER_A =
            UUID.fromString("a9810000-0000-0000-0000-000000000001");
    private static final UUID USER_B =
            UUID.fromString("b9810000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A =
            UUID.fromString("a9820000-0000-0000-0000-000000000001");
    private static final UUID WORKER_B =
            UUID.fromString("b9820000-0000-0000-0000-000000000002");
    private static final UUID EXPIRED_REFRESH_TOKEN_ID =
            UUID.fromString("a9870000-0000-0000-0000-000000000001");
    private static final UUID REVOKED_REFRESH_TOKEN_ID =
            UUID.fromString("a9870000-0000-0000-0000-000000000002");
    private static final UUID EXPIRED_REFRESH_FAMILY =
            UUID.fromString("a9880000-0000-0000-0000-000000000001");
    private static final UUID REVOKED_REFRESH_FAMILY =
            UUID.fromString("a9880000-0000-0000-0000-000000000002");
    private static final UUID TASK_A =
            UUID.fromString("a9830000-0000-0000-0000-000000000001");
    private static final UUID TASK_B =
            UUID.fromString("b9830000-0000-0000-0000-000000000002");
    private static final UUID CASE_A =
            UUID.fromString("a9840000-0000-0000-0000-000000000001");
    private static final UUID CASE_B =
            UUID.fromString("b9840000-0000-0000-0000-000000000002");
    private static final UUID ACTIVE_LINK =
            UUID.fromString("a9850000-0000-0000-0000-000000000001");
    private static final UUID EXPIRED_LINK =
            UUID.fromString("a9850000-0000-0000-0000-000000000002");
    private static final UUID REVOKED_LINK =
            UUID.fromString("a9850000-0000-0000-0000-000000000003");
    static final UUID UNBOUND_INSERT_WORKER =
            UUID.fromString("a9860000-0000-0000-0000-000000000001");
    private static final Instant FIXTURE_TIME = Instant.parse("2026-08-06T02:00:00Z");
    private static final List<String> RLS_TABLES = List.of(
            "company",
            "user_account",
            "refresh_token",
            "worker",
            "worker_link",
            "worker_response",
            "audit_event"
    );

    private JdbcTemplate jdbc;
    private TransactionTemplate transactionTemplate;
    private WorkerSnapshot workerBOriginal;
    private boolean rowsCreated;

    List<String> rlsTables() {
        return RLS_TABLES;
    }

    void prepare(DriverManagerDataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)
        );
        transactionTemplate.executeWithoutResult(status -> {
            assertFixtureIdsAreAvailable();
            String passwordHash = new BCryptPasswordEncoder(4).encode(PASSWORD);
            insertCompany(COMPANY_A, "Restricted HTTP Tenant A");
            insertCompany(COMPANY_B, "Restricted HTTP Tenant B");
            insertUser(USER_A, COMPANY_A, USER_A_EMAIL, passwordHash);
            insertUser(USER_B, COMPANY_B, USER_B_EMAIL, passwordHash);
            insertRefreshToken(
                    EXPIRED_REFRESH_TOKEN_ID,
                    EXPIRED_REFRESH_FAMILY,
                    EXPIRED_REFRESH_TOKEN,
                    FIXTURE_TIME.minusSeconds(172_800),
                    FIXTURE_TIME.minusSeconds(86_400),
                    null
            );
            insertRefreshToken(
                    REVOKED_REFRESH_TOKEN_ID,
                    REVOKED_REFRESH_FAMILY,
                    REVOKED_REFRESH_TOKEN,
                    FIXTURE_TIME,
                    FIXTURE_TIME.plusSeconds(315_360_000),
                    FIXTURE_TIME.plusSeconds(1)
            );
            insertWorker(WORKER_A, COMPANY_A, "Restricted Worker A");
            insertWorker(WORKER_B, COMPANY_B, "Restricted Worker B");
            insertTask(TASK_A, CASE_A, COMPANY_A, WORKER_A, USER_A);
            insertTask(TASK_B, CASE_B, COMPANY_B, WORKER_B, USER_B);
            insertWorkerLink(
                    ACTIVE_LINK,
                    TASK_A,
                    COMPANY_A,
                    USER_A,
                    ACTIVE_WORKER_LINK_TOKEN,
                    "ACTIVE",
                    FIXTURE_TIME,
                    FIXTURE_TIME.plusSeconds(315_360_000)
            );
            insertWorkerLink(
                    EXPIRED_LINK,
                    TASK_A,
                    COMPANY_A,
                    USER_A,
                    EXPIRED_WORKER_LINK_TOKEN,
                    "ACTIVE",
                    FIXTURE_TIME.minusSeconds(172_800),
                    FIXTURE_TIME.minusSeconds(86_400)
            );
            insertWorkerLink(
                    REVOKED_LINK,
                    TASK_A,
                    COMPANY_A,
                    USER_A,
                    REVOKED_WORKER_LINK_TOKEN,
                    "REVOKED",
                    FIXTURE_TIME,
                    FIXTURE_TIME.plusSeconds(315_360_000)
            );
        });
        rowsCreated = true;
        workerBOriginal = workerSnapshot(WORKER_B);
    }

    void cleanup() {
        if (!rowsCreated) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.update(
                    "DELETE FROM public.audit_event WHERE company_id IN (?, ?)",
                    COMPANY_A,
                    COMPANY_B
            );
            jdbc.update(
                    "DELETE FROM public.refresh_token WHERE company_id IN (?, ?)",
                    COMPANY_A,
                    COMPANY_B
            );
            jdbc.update(
                    "DELETE FROM public.worker_response WHERE company_id IN (?, ?)",
                    COMPANY_A,
                    COMPANY_B
            );
            jdbc.update(
                    "DELETE FROM public.worker_link WHERE company_id IN (?, ?)",
                    COMPANY_A,
                    COMPANY_B
            );
            jdbc.update("DELETE FROM public.task WHERE task_id IN (?, ?)", TASK_A, TASK_B);
            jdbc.update(
                    "DELETE FROM public.worker WHERE company_id IN (?, ?) OR worker_id = ?",
                    COMPANY_A,
                    COMPANY_B,
                    UNBOUND_INSERT_WORKER
            );
            jdbc.update(
                    "DELETE FROM public.user_account WHERE company_id IN (?, ?)",
                    COMPANY_A,
                    COMPANY_B
            );
            jdbc.update(
                    "DELETE FROM public.company WHERE company_id IN (?, ?)",
                    COMPANY_A,
                    COMPANY_B
            );
        });
        rowsCreated = false;
    }

    void assertTenantBWorkerUnchanged() {
        assertThat(workerSnapshot(WORKER_B)).isEqualTo(workerBOriginal);
    }

    void assertUnboundWorkerWasNotInserted() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.worker WHERE worker_id = ?",
                Integer.class,
                UNBOUND_INSERT_WORKER
        )).isZero();
    }

    int companyWorkerCount(UUID companyId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.worker WHERE company_id = ?",
                Integer.class,
                companyId
        );
        return count == null ? 0 : count;
    }

    UUID companyA() {
        return COMPANY_A;
    }

    UUID companyB() {
        return COMPANY_B;
    }

    UUID workerA() {
        return WORKER_A;
    }

    UUID workerB() {
        return WORKER_B;
    }

    UUID unboundInsertWorker() {
        return UNBOUND_INSERT_WORKER;
    }

    private void assertFixtureIdsAreAvailable() {
        Integer count = jdbc.queryForObject(
                """
                SELECT
                    (SELECT COUNT(*) FROM public.company WHERE company_id IN (?, ?))
                  + (SELECT COUNT(*) FROM public.user_account WHERE user_id IN (?, ?))
                  + (SELECT COUNT(*) FROM public.refresh_token
                     WHERE refresh_token_id IN (?, ?) OR token_hash IN (?, ?))
                  + (SELECT COUNT(*) FROM public.worker WHERE worker_id IN (?, ?, ?))
                  + (SELECT COUNT(*) FROM public.task WHERE task_id IN (?, ?))
                  + (SELECT COUNT(*) FROM public.worker_link
                     WHERE worker_link_id IN (?, ?, ?) OR token_hash IN (?, ?, ?))
                """,
                Integer.class,
                COMPANY_A,
                COMPANY_B,
                USER_A,
                USER_B,
                EXPIRED_REFRESH_TOKEN_ID,
                REVOKED_REFRESH_TOKEN_ID,
                sha256(EXPIRED_REFRESH_TOKEN),
                sha256(REVOKED_REFRESH_TOKEN),
                WORKER_A,
                WORKER_B,
                UNBOUND_INSERT_WORKER,
                TASK_A,
                TASK_B,
                ACTIVE_LINK,
                EXPIRED_LINK,
                REVOKED_LINK,
                sha256(ACTIVE_WORKER_LINK_TOKEN),
                sha256(EXPIRED_WORKER_LINK_TOKEN),
                sha256(REVOKED_WORKER_LINK_TOKEN)
        );
        if (count == null || count != 0) {
            throw new IllegalStateException(
                    "PostgreSQL restricted-role HTTP fixture IDs already exist; "
                            + "use an isolated test database or remove stale test rows"
            );
        }
    }

    private void insertCompany(UUID companyId, String name) {
        jdbc.update(
                """
                INSERT INTO public.company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, ?, 'ACTIVE', ?, ?, 0)
                """,
                companyId,
                name,
                Timestamp.from(FIXTURE_TIME),
                Timestamp.from(FIXTURE_TIME)
        );
    }

    private void insertUser(
            UUID userId,
            UUID companyId,
            String email,
            String passwordHash
    ) {
        jdbc.update(
                """
                INSERT INTO public.user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE', ?, ?, 0)
                """,
                userId,
                companyId,
                email,
                email,
                passwordHash,
                Timestamp.from(FIXTURE_TIME),
                Timestamp.from(FIXTURE_TIME)
        );
    }

    private void insertWorker(UUID workerId, UUID companyId, String displayName) {
        jdbc.update(
                """
                INSERT INTO public.worker (
                    worker_id, company_id, display_name, nationality_code,
                    preferred_language, work_status, stay_expiry_date,
                    contract_start_date, contract_end_date,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, 'VN', 'ko', 'ACTIVE', ?, ?, ?, ?, ?, 0)
                """,
                workerId,
                companyId,
                displayName,
                LocalDate.of(2027, 8, 31),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31),
                Timestamp.from(FIXTURE_TIME),
                Timestamp.from(FIXTURE_TIME)
        );
    }

    private void insertRefreshToken(
            UUID refreshTokenId,
            UUID familyId,
            String rawToken,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt
    ) {
        Instant updatedAt = revokedAt == null ? createdAt : revokedAt;
        jdbc.update(
                """
                INSERT INTO public.refresh_token (
                    refresh_token_id, user_id, company_id, token_family_id,
                    token_hash, expires_at, used_at, revoked_at,
                    replaced_by_token_id, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, NULL, ?, ?, 0)
                """,
                refreshTokenId,
                USER_A,
                COMPANY_A,
                familyId,
                sha256(rawToken),
                Timestamp.from(expiresAt),
                revokedAt == null ? null : Timestamp.from(revokedAt),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
        );
    }

    private void insertTask(
            UUID taskId,
            UUID caseId,
            UUID companyId,
            UUID workerId,
            UUID userId
    ) {
        jdbc.update(
                """
                INSERT INTO public.task (
                    task_id, company_id, worker_id, case_id, task_type,
                    workflow_id, workflow_catalog_version, title, description,
                    business_data_json, critical_fingerprint, content_revision,
                    source, status, due_date, created_by, updated_by,
                    created_at, updated_at, version
                ) VALUES (
                    ?, ?, ?, ?, 'RECONTRACT', 'restricted-http-test', '1',
                    'Restricted role HTTP task', NULL, '{}', ?, 0,
                    'MANUAL', 'WAITING_WORKER', ?, ?, ?, ?, ?, 0
                )
                """,
                taskId,
                companyId,
                workerId,
                caseId,
                "a".repeat(64),
                LocalDate.of(2027, 8, 1),
                userId,
                userId,
                Timestamp.from(FIXTURE_TIME),
                Timestamp.from(FIXTURE_TIME)
        );
    }

    private void insertWorkerLink(
            UUID workerLinkId,
            UUID taskId,
            UUID companyId,
            UUID issuedBy,
            String rawToken,
            String status,
            Instant createdAt,
            Instant expiresAt
    ) {
        jdbc.update(
                """
                INSERT INTO public.worker_link (
                    worker_link_id, task_id, company_id, token_hash, expires_at,
                    status, conversation_status, assignee_id, issued_by,
                    replaces_link_id, idempotency_key, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'WAITING_WORKER', NULL, ?, NULL, ?, ?, ?, 0)
                """,
                workerLinkId,
                taskId,
                companyId,
                sha256(rawToken),
                Timestamp.from(expiresAt),
                status,
                issuedBy,
                "restricted-http-" + workerLinkId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private WorkerSnapshot workerSnapshot(UUID workerId) {
        return jdbc.queryForObject(
                """
                SELECT worker_id, company_id, display_name, work_status, version
                FROM public.worker
                WHERE worker_id = ?
                """,
                (resultSet, rowNumber) -> new WorkerSnapshot(
                        resultSet.getObject("worker_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        resultSet.getString("display_name"),
                        resultSet.getString("work_status"),
                        resultSet.getLong("version")
                ),
                workerId
        );
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record WorkerSnapshot(
            UUID workerId,
            UUID companyId,
            String displayName,
            String workStatus,
            long version
    ) {
    }
}
