package com.fowoco.server.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.worker.application.port.WorkerAiContextReader;
import com.fowoco.server.worker.application.port.WorkerTaskContextReader;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkerArchiveIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("68000000-0000-0000-0000-000000000001");
    private static final UUID HR_A = UUID.fromString("68100000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_A = UUID.fromString("68100000-0000-0000-0000-000000000002");
    private static final String HR_EMAIL = "hr.archive@example.com";
    private static final String VIEWER_EMAIL = "viewer.archive@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WorkerAiContextReader aiContextReader;

    @Autowired
    private WorkerTaskContextReader taskContextReader;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedActor() {
        cleanupData();
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, '보관 테스트 사업장', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_A
        );
        insertUser(HR_A, HR_EMAIL, "HR");
        insertUser(VIEWER_A, VIEWER_EMAIL, "VIEWER");
    }

    @BeforeEach
    void resetWorkers() {
        cleanupOperationalData();
    }

    @AfterEach
    void removeArchivesForOtherTestContexts() {
        cleanupOperationalData();
    }

    @Test
    void activeWorkerCannotBeArchived() throws Exception {
        String token = accessToken(login(HR_EMAIL));
        String workerId = registerWorker(token, "재직 중 근로자");

        HttpResponse<String> eligibility = get(
                "/api/v1/workers/" + workerId + "/archive-eligibility", token);
        HttpResponse<String> archive = post(
                "/api/v1/workers/" + workerId + "/archive",
                "{\"reason\":\"정리\",\"expected_version\":0}",
                token
        );

        assertThat(eligibility.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(eligibility.body(), "$.archivable")).isFalse();
        assertThat(JsonPath.<java.util.List<String>>read(eligibility.body(), "$.blockers"))
                .contains("ACTIVE_EMPLOYMENT_STATUS");
        assertThat(archive.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(archive.body(), "$.code"))
                .isEqualTo("WORKER_ARCHIVE_NOT_ALLOWED");
    }

    @Test
    void resignedWorkerIsArchivedAndRemovedFromOperationalContexts() throws Exception {
        String token = accessToken(login(HR_EMAIL));
        String workerId = registerWorker(token, "보관 대상 근로자");
        assertThat(patch(
                "/api/v1/workers/" + workerId,
                "{\"work_status\":\"RESIGNED\",\"expected_version\":0}",
                token
        ).statusCode()).isEqualTo(200);

        HttpResponse<String> archive = post(
                "/api/v1/workers/" + workerId + "/archive",
                "{\"reason\":\"퇴사 후 행정업무 종료\",\"expected_version\":1}",
                token
        );

        assertThat(archive.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<Number>read(archive.body(), "$.worker_version").longValue())
                .isEqualTo(2);
        assertThat(get("/api/v1/workers", token).body()).doesNotContain("보관 대상 근로자");
        assertThat(get("/api/v1/workers/" + workerId, token).statusCode()).isEqualTo(200);
        assertThat(aiContextReader.findByDisplayName(COMPANY_A, "보관 대상 근로자")).isEmpty();
        assertThat(taskContextReader.findByIdAndCompanyId(UUID.fromString(workerId), COMPANY_A))
                .isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_ARCHIVED'",
                Integer.class,
                UUID.fromString(workerId)
        )).isEqualTo(1);
    }

    @Test
    void openTaskBlocksArchiveUntilOperationalWorkIsClosed() throws Exception {
        String token = accessToken(login(HR_EMAIL));
        String workerId = registerWorker(token, "업무 남은 근로자");
        patch(
                "/api/v1/workers/" + workerId,
                "{\"work_status\":\"TERMINATED\",\"expected_version\":0}",
                token
        );
        insertOpenTask(UUID.fromString(workerId));

        HttpResponse<String> response = get(
                "/api/v1/workers/" + workerId + "/archive-eligibility", token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<String>>read(response.body(), "$.blockers"))
                .contains("OPEN_TASK");
    }

    @Test
    void pendingApprovalAndActiveWorkerLinkAreReportedAsBlockers() throws Exception {
        String token = accessToken(login(HR_EMAIL));
        String workerId = registerWorker(token, "승인·링크 남은 근로자");
        patch(
                "/api/v1/workers/" + workerId,
                "{\"work_status\":\"RESIGNED\",\"expected_version\":0}",
                token
        );
        UUID taskId = insertOpenTask(UUID.fromString(workerId));
        insertPendingApproval(taskId);
        insertActiveWorkerLink(taskId);

        HttpResponse<String> response = get(
                "/api/v1/workers/" + workerId + "/archive-eligibility", token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<String>>read(response.body(), "$.blockers"))
                .contains("OPEN_TASK", "PENDING_APPROVAL", "ACTIVE_WORKER_LINK");
    }

    @Test
    void staleVersionAndViewerWriteAreRejected() throws Exception {
        String hrToken = accessToken(login(HR_EMAIL));
        String viewerToken = accessToken(login(VIEWER_EMAIL));
        String workerId = registerWorker(hrToken, "권한 테스트 근로자");
        patch(
                "/api/v1/workers/" + workerId,
                "{\"work_status\":\"RESIGNED\",\"expected_version\":0}",
                hrToken
        );

        HttpResponse<String> stale = post(
                "/api/v1/workers/" + workerId + "/archive",
                "{\"reason\":\"퇴사\",\"expected_version\":0}",
                hrToken
        );
        HttpResponse<String> forbidden = post(
                "/api/v1/workers/" + workerId + "/archive",
                "{\"reason\":\"퇴사\",\"expected_version\":1}",
                viewerToken
        );

        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(stale.body(), "$.code"))
                .isEqualTo("WORKER_ARCHIVE_VERSION_CONFLICT");
        assertThat(forbidden.statusCode()).isEqualTo(403);
    }

    private String registerWorker(String token, String displayName) throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/workers",
                "{\"display_name\":\"%s\"}".formatted(displayName),
                token
        );
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    private UUID insertOpenTask(UUID workerId) {
        UUID taskId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id, company_id, worker_id, case_id, task_type,
                    workflow_id, workflow_catalog_version, title, description,
                    business_data_json, critical_fingerprint, content_revision,
                    source, status, created_by, updated_by, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'DOCUMENT_REQUEST',
                          'WF-DOC-001', '0.3.1', '남은 업무', '완료 전 업무',
                          '{}', ?, 0, 'MANUAL', 'DRAFT', ?, ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                taskId,
                COMPANY_A,
                workerId,
                caseId,
                "f".repeat(64),
                HR_A,
                HR_A
        );
        return taskId;
    }

    private void insertPendingApproval(UUID taskId) {
        jdbcTemplate.update(
                """
                INSERT INTO approval_request (
                    approval_request_id, task_id, company_id, target_task_version,
                    target_content_revision, target_fingerprint, status,
                    hr_snapshot_json, changed_fields_json, source_versions_json,
                    requested_by, requested_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, 0, 0, ?, 'PENDING', '{}', '[]', '{}', ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                UUID.randomUUID(),
                taskId,
                COMPANY_A,
                "f".repeat(64),
                HR_A
        );
    }

    private void insertActiveWorkerLink(UUID taskId) {
        jdbcTemplate.update(
                """
                INSERT INTO worker_link (
                    worker_link_id, task_id, company_id, token_hash, expires_at,
                    status, conversation_status, issued_by, idempotency_key,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, DATEADD('DAY', 1, CURRENT_TIMESTAMP), 'ACTIVE',
                          'WAITING_WORKER', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                UUID.randomUUID(),
                taskId,
                COMPANY_A,
                "a".repeat(64),
                HR_A,
                "archive-test-" + UUID.randomUUID()
        );
    }

    private void insertUser(UUID userId, String email, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                userId,
                COMPANY_A,
                email,
                email,
                passwordEncoder.encode(PASSWORD),
                role
        );
    }

    private void cleanupOperationalData() {
        jdbcTemplate.update("DELETE FROM worker_archive");
        jdbcTemplate.update("DELETE FROM audit_event WHERE company_id = ?", COMPANY_A);
        jdbcTemplate.update("DELETE FROM approval_request WHERE company_id = ?", COMPANY_A);
        jdbcTemplate.update("DELETE FROM worker_link WHERE company_id = ?", COMPANY_A);
        jdbcTemplate.update("DELETE FROM task WHERE company_id = ?", COMPANY_A);
        jdbcTemplate.update("DELETE FROM worker_document WHERE company_id = ?", COMPANY_A);
        jdbcTemplate.update("DELETE FROM worker WHERE company_id = ?", COMPANY_A);
    }

    private void cleanupData() {
        jdbcTemplate.update("DELETE FROM worker_archive");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM worker_link");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    private HttpResponse<String> login(String email) throws Exception {
        return post(
                "/api/v1/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD),
                null
        );
    }

    private String accessToken(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        return send(path, body, token, "POST");
    }

    private HttpResponse<String> patch(String path, String body, String token) throws Exception {
        return send(path, body, token, "PATCH");
    }

    private HttpResponse<String> send(String path, String body, String token, String method)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
