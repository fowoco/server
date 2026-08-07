package com.fowoco.server.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
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
        createTask(accessToken, workerId, "READY_FOR_REVIEW_후보1");
        createTask(accessToken, workerId, "READY_FOR_REVIEW_후보2");

        HttpResponse<String> response = authorizedGet("/api/v1/dashboard/today", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.priority_tasks")).hasSize(2);
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
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
