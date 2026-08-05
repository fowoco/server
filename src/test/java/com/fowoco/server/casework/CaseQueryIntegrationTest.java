package com.fowoco.server.casework;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaseQueryIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("ca000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("cb000000-0000-0000-0000-000000000001");
    private static final UUID HR_A = UUID.fromString("ca100000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("cb100000-0000-0000-0000-000000000001");
    private static final UUID WORKER_A = UUID.fromString("ca200000-0000-0000-0000-000000000001");
    private static final UUID WORKER_B = UUID.fromString("cb200000-0000-0000-0000-000000000001");
    private static final UUID CASE_A = UUID.fromString("ca300000-0000-0000-0000-000000000001");
    private static final UUID CASE_B = UUID.fromString("cb300000-0000-0000-0000-000000000001");
    private static final UUID TASK_A_DONE = UUID.fromString("ca400000-0000-0000-0000-000000000001");
    private static final UUID TASK_A_WAITING = UUID.fromString("ca400000-0000-0000-0000-000000000002");
    private static final UUID TASK_B = UUID.fromString("cb400000-0000-0000-0000-000000000001");
    private static final String PASSWORD = "Test-password-1!";
    private static final String HR_A_EMAIL = "case.hr.a@example.com";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetAndSeed() {
        cleanDatabase();

        insertCompany(COMPANY_A, "Case 테스트 사업장 A");
        insertCompany(COMPANY_B, "Case 테스트 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash);
        insertUser(HR_B, COMPANY_B, "case.hr.b@example.com", passwordHash);
        insertWorker(WORKER_A, COMPANY_A, "응웬반안");
        insertWorker(WORKER_B, COMPANY_B, "다른 사업장 근로자");
        insertCase(CASE_A, COMPANY_A, WORKER_A, HR_A, "재계약·연장 준비", "URGENT");
        insertCase(CASE_B, COMPANY_B, WORKER_B, HR_B, "다른 사업장 Case", "NORMAL");
        insertTask(TASK_A_DONE, CASE_A, COMPANY_A, WORKER_A, HR_A, "RECONTRACT", "COMPLETED", 10);
        insertTask(
                TASK_A_WAITING,
                CASE_A,
                COMPANY_A,
                WORKER_A,
                HR_A,
                "STAY_PERIOD_EXTENSION",
                "WAITING_WORKER",
                5
        );
        insertTask(TASK_B, CASE_B, COMPANY_B, WORKER_B, HR_B, "RECONTRACT", "DRAFT", 20);
        insertWorkerResponse();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM worker_response");
        jdbcTemplate.update("DELETE FROM worker_link");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM workflow_case");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void returnsCaseProjectionOnlyInsideTheAuthenticatedCompany() throws Exception {
        String token = login();

        HttpResponse<String> page = get("/api/v1/cases?page=0&size=20", token);

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(page.body(), "$.total_elements").intValue()).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(page.body(), "$.items[*].case_id"))
                .containsExactly(CASE_A.toString());
        assertThat(JsonPath.<String>read(page.body(), "$.items[0].display_status"))
                .isEqualTo("REVIEW_REQUIRED");
        assertThat(JsonPath.<Boolean>read(page.body(), "$.items[0].has_unread_response")).isTrue();
        assertThat(JsonPath.<Number>read(page.body(), "$.items[0].progress.percentage").intValue())
                .isEqualTo(50);

        HttpResponse<String> detail = get("/api/v1/cases/" + CASE_A + "/projection", token);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(detail.body(), "$.worker_display_name"))
                .isEqualTo("응웬반안");
        assertThat(JsonPath.<String>read(detail.body(), "$.current_task.task_id"))
                .isEqualTo(TASK_A_WAITING.toString());
        assertThat(JsonPath.<List<?>>read(detail.body(), "$.tasks")).hasSize(2);
        assertThat(JsonPath.<Number>read(detail.body(), "$.readiness.worker_responses").intValue())
                .isEqualTo(1);
        assertThat(JsonPath.<Number>read(detail.body(), "$.readiness.pending_approvals").intValue())
                .isZero();

        HttpResponse<String> hidden = get("/api/v1/cases/" + CASE_B + "/projection", token);
        assertThat(hidden.statusCode()).isEqualTo(404);
    }

    @Test
    void documentsCaseEndpointsInOpenApi() throws Exception {
        HttpResponse<String> response = getWithoutToken("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.paths['/api/v1/cases'].get.operationId"))
                .isEqualTo("listCases");
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/cases/{caseId}/projection'].get.operationId"
        )).isEqualTo("getCaseProjection");
    }

    private String login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(HR_A_EMAIL, PASSWORD)
                ))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

    private HttpResponse<String> getWithoutToken(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void insertCompany(UUID id, String name) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status, created_at, updated_at, version)"
                        + " VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id,
                name
        );
    }

    private void insertUser(UUID id, UUID companyId, String email, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                id,
                companyId,
                email,
                email,
                passwordHash
        );
    }

    private void insertWorker(UUID id, UUID companyId, String displayName) {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, work_status,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                id,
                companyId,
                displayName
        );
    }

    private void insertCase(
            UUID caseId,
            UUID companyId,
            UUID workerId,
            UUID actorId,
            String title,
            String priority
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO workflow_case (
                    case_id, company_id, worker_id, title, lifecycle_status, priority,
                    workflow_catalog_version, workflow_snapshot_json, created_by,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, '0.2.0', '{}', ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                caseId,
                companyId,
                workerId,
                title,
                priority,
                actorId
        );
    }

    private void insertTask(
            UUID taskId,
            UUID caseId,
            UUID companyId,
            UUID workerId,
            UUID actorId,
            String taskType,
            String status,
            int dueDays
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id, company_id, worker_id, case_id, task_type, workflow_id,
                    workflow_catalog_version, title, description, business_data_json,
                    critical_fingerprint, content_revision, source, status, due_date,
                    created_by, updated_by, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'WF-DEMO-001', '0.2.0', ?, NULL, '{}', ?, 0,
                          'MANUAL', ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                taskId,
                companyId,
                workerId,
                caseId,
                taskType,
                taskType + " 준비",
                "a".repeat(64),
                status,
                LocalDate.now().plusDays(dueDays),
                actorId,
                actorId
        );
    }

    private void insertWorkerResponse() {
        UUID linkId = UUID.fromString("ca500000-0000-0000-0000-000000000001");
        jdbcTemplate.update(
                """
                INSERT INTO worker_link (
                    worker_link_id, task_id, company_id, token_hash, expires_at, status,
                    conversation_status, issued_by, idempotency_key,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, DATEADD('DAY', 1, CURRENT_TIMESTAMP), 'ACTIVE',
                          'NEEDS_FOLLOWUP', ?, 'case-test-link',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                linkId,
                TASK_A_WAITING,
                COMPANY_A,
                "b".repeat(64),
                HR_A
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker_response (
                    response_id, worker_link_id, company_id, response_type,
                    message, idempotency_key, received_at
                ) VALUES (?, ?, ?, 'QUESTION', '질문이 있습니다.', 'case-test-response', CURRENT_TIMESTAMP)
                """,
                UUID.fromString("ca600000-0000-0000-0000-000000000001"),
                linkId,
                COMPANY_A
        );
    }
}
