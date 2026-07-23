package com.fowoco.server.approval;

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
class ApprovalAuditIntegrationTest {

    private static final UUID COMPANY_A =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_A =
            UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID HR_A =
            UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_A =
            UUID.fromString("a3000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_B =
            UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_A =
            UUID.fromString("a4000000-0000-0000-0000-000000000001");
    private static final UUID TASK_A =
            UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID CASE_A =
            UUID.fromString("a6000000-0000-0000-0000-000000000001");
    private static final String PASSWORD = "Test-password-1!";
    private static final String ADMIN_A_EMAIL = "approval.admin.a@example.com";
    private static final String HR_A_EMAIL = "approval.hr.a@example.com";
    private static final String VIEWER_A_EMAIL = "approval.viewer.a@example.com";
    private static final String ADMIN_B_EMAIL = "approval.admin.b@example.com";
    private static final String FINGERPRINT = "a".repeat(64);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetAndSeed() {
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "승인 테스트 사업장 A");
        insertCompany(COMPANY_B, "승인 테스트 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(ADMIN_A, COMPANY_A, ADMIN_A_EMAIL, passwordHash, "ADMIN");
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash, "HR");
        insertUser(VIEWER_A, COMPANY_A, VIEWER_A_EMAIL, passwordHash, "VIEWER");
        insertUser(ADMIN_B, COMPANY_B, ADMIN_B_EMAIL, passwordHash, "ADMIN");
        insertWorker();
        insertDraftTask();
    }

    @Test
    void approvedTaskCanBeSubmittedEvidencedCompletedAndAudited() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> approvalRequest = requestApproval(hrToken, validApprovalBody());
        assertThat(approvalRequest.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(approvalRequest.body(), "$.approval_status"))
                .isEqualTo("PENDING");
        assertThat(JsonPath.<String>read(approvalRequest.body(), "$.task_status"))
                .isEqualTo("READY_FOR_REVIEW");
        assertThat(JsonPath.<Number>read(approvalRequest.body(), "$.task_version").longValue())
                .isEqualTo(1);

        HttpResponse<String> approve = authorizedPost(
                taskPath("/approve"),
                """
                {"expected_version":1,"reason":"계약조건 확인 완료"}
                """,
                hrToken
        );
        assertThat(approve.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(approve.body(), "$.task_status")).isEqualTo("APPROVED");

        HttpResponse<String> submission = authorizedPost(
                taskPath("/external-submissions"),
                """
                {
                  "expected_version":2,
                  "destination":"고용센터",
                  "safe_reference":"접수-2026-001"
                }
                """,
                hrToken
        );
        assertThat(submission.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(submission.body(), "$.task_status"))
                .isEqualTo("WAITING_EXTERNAL");

        HttpResponse<String> evidence = authorizedPost(
                taskPath("/evidence"),
                """
                {
                  "evidence_type":"OFFICIAL_RESULT",
                  "file_reference":"file-ref-001",
                  "note":"승인 결과 확인"
                }
                """,
                hrToken
        );
        assertThat(evidence.statusCode()).isEqualTo(201);

        HttpResponse<String> complete = authorizedPost(
                taskPath("/complete"),
                """
                {"expected_version":3}
                """,
                hrToken
        );
        assertThat(complete.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(complete.body(), "$.task_status"))
                .isEqualTo("COMPLETED");

        HttpResponse<String> activities = authorizedGet(taskPath("/activities"), hrToken);
        assertThat(activities.statusCode()).isEqualTo(200);
        List<String> actions = JsonPath.read(activities.body(), "$[*].action");
        assertThat(actions).containsExactly(
                "APPROVAL_REQUESTED",
                "TASK_APPROVED",
                "EXTERNAL_SUBMISSION_RECORDED",
                "EVIDENCE_RECORDED",
                "TASK_COMPLETED"
        );
        assertThat(activities.body())
                .doesNotContain("ai_snapshot")
                .doesNotContain("hr_snapshot")
                .doesNotContain("file-ref-001");

        String adminToken = accessToken(login(ADMIN_A_EMAIL));
        HttpResponse<String> auditPage = authorizedGet(
                "/api/v1/audit-events?target_type=TASK&target_id=" + TASK_A + "&limit=2",
                adminToken
        );
        assertThat(auditPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<?>>read(auditPage.body(), "$.items")).hasSize(2);
        String nextCursor = JsonPath.read(auditPage.body(), "$.next_cursor");
        assertThat(nextCursor).isNotBlank();
        List<String> firstIds = JsonPath.read(auditPage.body(), "$.items[*].audit_event_id");

        HttpResponse<String> secondAuditPage = authorizedGet(
                "/api/v1/audit-events?target_type=TASK&target_id=" + TASK_A
                        + "&limit=2&cursor=" + nextCursor,
                adminToken
        );
        assertThat(secondAuditPage.statusCode()).isEqualTo(200);
        List<String> secondIds = JsonPath.read(
                secondAuditPage.body(),
                "$.items[*].audit_event_id"
        );
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
    }

    @Test
    void sensitiveSnapshotRollsBackTaskTransitionApprovalAndAuditTogether() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String body = """
                {
                  "expected_version":0,
                  "requirements_satisfied":true,
                  "ai_snapshot":null,
                  "hr_snapshot":{"phone":"010-1234-5678"},
                  "changed_fields":[],
                  "source_versions":{"workflow_catalog_version":"2026.07"}
                }
                """;

        HttpResponse<String> response = requestApproval(hrToken, body);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("SENSITIVE_SNAPSHOT_REJECTED");
        assertThat(taskStatus()).isEqualTo("DRAFT");
        assertThat(taskVersion()).isZero();
        assertThat(count("approval_request")).isZero();
        assertThat(count("task_transition_history")).isZero();
        assertThat(count("audit_event")).isZero();
    }

    @Test
    void viewerCannotWriteAndHrCannotUseAdminAuditSearch() throws Exception {
        String viewerToken = accessToken(login(VIEWER_A_EMAIL));
        HttpResponse<String> viewerWrite = requestApproval(viewerToken, validApprovalBody());
        assertThat(viewerWrite.statusCode()).isEqualTo(403);

        String hrToken = accessToken(login(HR_A_EMAIL));
        HttpResponse<String> hrAudit = authorizedGet("/api/v1/audit-events", hrToken);
        assertThat(hrAudit.statusCode()).isEqualTo(403);
    }

    @Test
    void anotherCompanyCannotDiscoverTaskOrActivities() throws Exception {
        String otherCompanyAdminToken = accessToken(login(ADMIN_B_EMAIL));

        HttpResponse<String> activities = authorizedGet(
                taskPath("/activities"),
                otherCompanyAdminToken
        );
        HttpResponse<String> write = requestApproval(
                otherCompanyAdminToken,
                validApprovalBody()
        );

        assertThat(activities.statusCode()).isEqualTo(404);
        assertThat(write.statusCode()).isEqualTo(404);
    }

    @Test
    void staleVersionCannotApproveAndLeavesPendingReviewUnchanged() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        assertThat(requestApproval(hrToken, validApprovalBody()).statusCode()).isEqualTo(201);

        HttpResponse<String> staleApprove = authorizedPost(
                taskPath("/approve"),
                """
                {"expected_version":0,"reason":"오래된 화면에서 승인"}
                """,
                hrToken
        );

        assertThat(staleApprove.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(staleApprove.body(), "$.code"))
                .isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(taskStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(taskVersion()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM approval_request WHERE task_id = ?",
                String.class,
                TASK_A
        )).isEqualTo("PENDING");
    }

    @Test
    void approvedTaskWithEvidenceCanCompleteWithoutEnteringExternalWaiting() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        assertThat(requestApproval(hrToken, validApprovalBody()).statusCode()).isEqualTo(201);
        assertThat(authorizedPost(
                taskPath("/approve"),
                """
                {"expected_version":1}
                """,
                hrToken
        ).statusCode()).isEqualTo(200);
        assertThat(authorizedPost(
                taskPath("/evidence"),
                """
                {"evidence_type":"HR_CONFIRMATION","note":"내부 재계약 완료 확인"}
                """,
                hrToken
        ).statusCode()).isEqualTo(201);

        HttpResponse<String> complete = authorizedPost(
                taskPath("/complete"),
                """
                {"expected_version":2}
                """,
                hrToken
        );

        assertThat(complete.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(complete.body(), "$.task_status"))
                .isEqualTo("COMPLETED");
        assertThat(count("external_submission")).isZero();
    }

    private HttpResponse<String> requestApproval(String token, String body) throws Exception {
        return authorizedPost(taskPath("/approval-requests"), body, token);
    }

    private String validApprovalBody() {
        return """
                {
                  "expected_version":0,
                  "requirements_satisfied":true,
                  "ai_snapshot":{
                    "intent":"EXPIRY_RENEWAL",
                    "confidence":0.94
                  },
                  "hr_snapshot":{
                    "worker_id":"%s",
                    "contract_end_date":"2027-08-31",
                    "monthly_wage":2500000
                  },
                  "changed_fields":["monthly_wage"],
                  "source_versions":{
                    "agent_version":"agent-1",
                    "workflow_catalog_version":"2026.07"
                  }
                }
                """.formatted(WORKER_A);
    }

    private HttpResponse<String> login(String email) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    private String accessToken(HttpResponse<String> loginResponse) {
        return JsonPath.read(loginResponse.body(), "$.access_token");
    }

    private HttpResponse<String> authorizedPost(String path, String body, String token)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authorizedGet(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String taskPath(String suffix) {
        return "/api/v1/tasks/" + TASK_A + suffix;
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                companyId,
                name
        );
    }

    private void insertUser(
            UUID userId,
            UUID companyId,
            String email,
            String passwordHash,
            String role
    ) {
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

    private void insertWorker() {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality, preferred_language,
                    employment_status, stay_expiry_date, contract_start_date, contract_end_date,
                    created_at, updated_at, version
                ) VALUES (?, ?, '근로자 A', 'VNM', 'vi', 'ACTIVE', ?, ?, ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                WORKER_A,
                COMPANY_A,
                LocalDate.of(2027, 8, 31),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31)
        );
    }

    private void insertDraftTask() {
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id, company_id, worker_id, case_id, task_type,
                    workflow_id, workflow_catalog_version, title, description,
                    business_data_json, critical_fingerprint, content_revision,
                    source, status, due_date,
                    created_by, updated_by, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'RECONTRACT',
                          'e9-recontract', '2026.07', '재계약 준비', '계약조건 확인',
                          '{}', ?, 0, 'MANUAL', 'DRAFT', ?,
                          ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                TASK_A,
                COMPANY_A,
                WORKER_A,
                CASE_A,
                FINGERPRINT,
                LocalDate.of(2027, 7, 31),
                HR_A,
                HR_A
        );
    }

    private String taskStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE task_id = ?",
                String.class,
                TASK_A
        );
    }

    private long taskVersion() {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM task WHERE task_id = ?",
                Long.class,
                TASK_A
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
