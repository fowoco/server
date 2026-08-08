package com.fowoco.server.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.List;
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
class DashboardSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("61000000-0000-0000-0000-000000000002");
    private static final UUID VIEWER_A = UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final String HR_A_EMAIL = "hr.dashboard.a@example.com";
    private static final String HR_B_EMAIL = "hr.dashboard.b@example.com";
    private static final String VIEWER_A_EMAIL = "viewer.dashboard.a@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedCompaniesAndUsers() {
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "대시보드 사업장 A");
        insertCompany(COMPANY_B, "대시보드 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash, "HR");
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash, "HR");
        insertUser(VIEWER_A, COMPANY_A, VIEWER_A_EMAIL, passwordHash, "VIEWER");
    }

    @BeforeEach
    void resetTaskState() {
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM worker");
    }

    @Test
    void emptyCompanyReturnsZeroCountsNotError() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(response.body(), "$.summary_counts.pending_approval").longValue())
                .isZero();
        assertThat(JsonPath.<Number>read(response.body(), "$.summary_counts.due_today").longValue())
                .isZero();
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.priority_tasks")).isEmpty();
    }

    @Test
    void countsMatchActualTaskStatuses() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(accessToken, "대시보드테스트근로자");
        String taskId1 = createTask(accessToken, workerId, "READY_FOR_REVIEW_후보1");
        createTask(accessToken, workerId, "READY_FOR_REVIEW_후보2");
        requestReview(accessToken, taskId1, workerId);

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.priority_tasks")).hasSize(2);
        assertThat(JsonPath.<Number>read(response.body(), "$.summary_counts.pending_approval").longValue())
                .isEqualTo(1);
        assertThat(JsonPath.<Number>read(response.body(), "$.approval_count").longValue())
                .isEqualTo(1);
    }

    @Test
    void otherCompanyDataIsNotVisible() throws Exception {
        String companyAToken = accessToken(login(HR_A_EMAIL));
        String companyBToken = accessToken(login(HR_B_EMAIL));
        String workerId = registerWorker(companyBToken, "타사업장근로자");
        createTask(companyBToken, workerId, "타사업장업무");

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", companyAToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.priority_tasks")).isEmpty();
    }

    @Test
    void viewerCanAccessDashboard() throws Exception {
        String viewerToken = accessToken(login(VIEWER_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", viewerToken);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void dateParameterOverridesServerToday() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet(
                "/api/v1/dashboard/today?date=2020-01-01", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void timezoneParameterIsAccepted() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet(
                "/api/v1/dashboard/today?timezone=Asia/Seoul", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void upcoming7DaysIncludesWorkerWithNearExpiry() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        registerWorkerWithStayExpiry(accessToken, "체류만료임박근로자", "2026-08-12");

        HttpResponse<String> response = authorizedGet(
                "/api/v1/dashboard/today?date=2026-08-08", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
        java.util.List<Object> upcoming = JsonPath.read(response.body(), "$.upcoming_7_days");
        assertThat(upcoming).isNotEmpty();
        List<String> categories = JsonPath.read(response.body(), "$.upcoming_7_days[*].category");
        assertThat(categories).contains("STAY_EXPIRY");
    }

    @Test
    void upcoming7DaysExcludesWorkerWithFarExpiry() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        registerWorkerWithStayExpiry(accessToken, "체류만료여유근로자", "2026-12-31");

        HttpResponse<String> response = authorizedGet(
                "/api/v1/dashboard/today?date=2026-08-08", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> names = JsonPath.read(response.body(), "$.upcoming_7_days[*].display_name");
        assertThat(names).doesNotContain("체류만료여유근로자");
    }

    @Test
    void upcoming7DaysIncludesAllExpiryCategories() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        registerWorkerWithAllExpiryFields(
                accessToken, "전체만료임박근로자",
                "2026-08-12", "2026-08-13", "2026-08-14", "2026-08-11"
        );
        String workerId = registerWorker(accessToken, "서류만료임박근로자");
        registerDocumentWithExpiry(accessToken, workerId, "2026-08-10");

        HttpResponse<String> response = authorizedGet(
                "/api/v1/dashboard/today?date=2026-08-08", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> categories = JsonPath.read(response.body(), "$.upcoming_7_days[*].category");
        assertThat(categories)
                .contains("STAY_EXPIRY", "CONTRACT_END", "EMPLOYMENT_PERMIT_END",
                        "EMPLOYMENT_ACTIVITY_END", "DOCUMENT_EXPIRY");
    }

    @Test
    void upcoming7DaysExcludesAlreadyPastExpiry() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        registerWorkerWithStayExpiry(accessToken, "이미지난만료근로자", "2026-07-01");

        HttpResponse<String> response = authorizedGet(
                "/api/v1/dashboard/today?date=2026-08-08", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> names = JsonPath.read(response.body(), "$.upcoming_7_days[*].display_name");
        assertThat(names).doesNotContain("이미지난만료근로자");
    }

    @Test
    void recommendationsConnectedCountMatchesOpenTasks() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(accessToken, "추천테스트근로자");
        createTask(accessToken, workerId, "추천테스트업무1");
        createTask(accessToken, workerId, "추천테스트업무2");

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(response.body(), "$.recommendations.connected_count").longValue())
                .isEqualTo(2);
    }

    @Test
    void recommendationsReviewIncludesReadyForReviewTask() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(accessToken, "승인대기추천근로자");
        String taskId = createTask(accessToken, workerId, "승인대기업무");
        requestReview(accessToken, taskId, workerId);

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> reviewTitles = JsonPath.read(response.body(), "$.recommendations.review[*].title");
        assertThat(reviewTitles).contains("승인대기업무");
    }

    @Test
    void recommendationsPreparedAndAfterApprovalAreEmptyByDefault() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.recommendations.prepared")).isEmpty();
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.recommendations.after_approval")).isEmpty();
    }

    private String registerWorkerWithAllExpiryFields(
            String accessToken,
            String displayName,
            String stayExpiryDate,
            String contractEndDate,
            String employmentPermitEndDate,
            String employmentActivityEndDate
    ) throws Exception {
        String body = """
                {
                  "display_name": "%s",
                  "stay_expiry_date": "%s",
                  "contract_end_date": "%s",
                  "employment_permit_end_date": "%s",
                  "employment_activity_end_date": "%s"
                }
                """.formatted(displayName, stayExpiryDate, contractEndDate,
                employmentPermitEndDate, employmentActivityEndDate);
        HttpResponse<String> response = postJson("/api/v1/workers", body, accessToken);
        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    private void registerDocumentWithExpiry(String accessToken, String workerId, String expiryDate)
            throws Exception {
        String body = """
                {"document_type": "PASSPORT_COPY", "submission_status": "SUBMITTED", "expiry_date": "%s"}
                """.formatted(expiryDate);
        HttpResponse<String> response = postJson(
                "/api/v1/workers/" + workerId + "/documents", body, accessToken
        );
        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(201);
    }

    private String registerWorkerWithStayExpiry(String accessToken, String displayName, String stayExpiryDate)
            throws Exception {
        String body = """
                {"display_name": "%s", "stay_expiry_date": "%s"}
                """.formatted(displayName, stayExpiryDate);
        HttpResponse<String> response = postJson("/api/v1/workers", body, accessToken);
        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    @Test
    void invalidTimezoneReturnsClientError() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet(
                "/api/v1/dashboard/today?timezone=Not/AValidZone", accessToken
        );

        assertThat(response.statusCode()).isNotEqualTo(500);
    }

    private String registerWorker(String accessToken, String displayName) throws Exception {
        String body = """
                {"display_name": "%s"}
                """.formatted(displayName);
        HttpResponse<String> response = postJson("/api/v1/workers", body, accessToken);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    private String createTask(String accessToken, String workerId, String title) throws Exception {
        String body = """
                {
                  "worker_id":"%s",
                  "task_type":"RECONTRACT",
                  "workflow_id":"WF-CON-001",
                  "title":"%s",
                  "description":"대시보드 테스트용",
                  "due_date":"2026-08-20",
                  "business_data":{"monthly_wage":2500000}
                }
                """.formatted(workerId, title);
        HttpResponse<String> response = postJson("/api/v1/tasks", body, accessToken);
        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.task_id");
    }

    private void requestReview(String accessToken, String taskId, String workerId) throws Exception {
        HttpResponse<String> taskResponse = authorizedGet("/api/v1/tasks/" + taskId, accessToken);
        List<String> checklistIds = JsonPath.read(taskResponse.body(), "$.checklist_items[*].checklist_item_id");
        for (String checklistId : checklistIds) {
            HttpResponse<String> checked = sendJson(
                    "/api/v1/tasks/" + taskId + "/checklist-items/" + checklistId,
                    """
                    {"completed":true,"expected_version":0,"expected_task_version":0}
                    """,
                    accessToken,
                    "PATCH"
            );
            assertThat(checked.statusCode()).as("body: %s", checked.body()).isEqualTo(200);
        }
        HttpResponse<String> approvalRequest = sendJson(
                "/api/v1/tasks/" + taskId + "/approval-requests",
                """
                {
                  "expected_version":0,
                  "ai_snapshot":null,
                  "hr_snapshot":{"worker_id":"%s"},
                  "changed_fields":[],
                  "source_versions":{"workflow_catalog_version":"0.2.0"}
                }
                """.formatted(workerId),
                accessToken,
                "POST"
        );
        assertThat(approvalRequest.statusCode()).as("body: %s", approvalRequest.body()).isEqualTo(201);
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                companyId,
                name
        );
    }

    private void insertUser(UUID userId, UUID companyId, String email, String passwordHash, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                userId,
                companyId,
                email,
                email,
                passwordHash,
                role
        );
    }

    private HttpResponse<String> login(String email) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
        return postJson("/api/v1/auth/login", body, null);
    }

    private String accessToken(HttpResponse<String> loginResponse) {
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        return JsonPath.read(loginResponse.body(), "$.access_token");
    }

    private HttpResponse<String> authorizedGet(String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String accessToken) throws Exception {
        return sendJson(path, body, accessToken, "POST");
    }

    private HttpResponse<String> sendJson(String path, String body, String accessToken, String method)
            throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
