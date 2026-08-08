package com.fowoco.server.workerlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
class WorkerLinkSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("A0000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("B0000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("A1000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("B1000000-0000-0000-0000-000000000002");
    private static final UUID VIEWER_A = UUID.fromString("A1000000-0000-0000-0000-000000000003");
    private static final String HR_A_EMAIL = "hr.link.a@example.com";
    private static final String HR_B_EMAIL = "hr.link.b@example.com";
    private static final String VIEWER_A_EMAIL = "viewer.link.a@example.com";
    private static final String PASSWORD = "Test-password-1!";
    private static final String BOUNDARY = "FowocoLinkTestBoundary1234";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedCompaniesAndUsers() {
        cleanupAll();
        insertCompany(COMPANY_A, "사업장 A");
        insertCompany(COMPANY_B, "사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash, "HR");
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash, "HR");
        insertUser(VIEWER_A, COMPANY_A, VIEWER_A_EMAIL, passwordHash, "VIEWER");
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM worker_response_upload");
        jdbcTemplate.update("DELETE FROM worker_response");
        jdbcTemplate.update("DELETE FROM worker_link");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
    }

    private void cleanupAll() {
        resetState();
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void fullFlow_issueViewUploadRespond_succeeds() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "전체흐름테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                "fullflow-issue-key"
        );
        assertThat(issueResponse.statusCode()).as("issue response body: %s", issueResponse.body()).isEqualTo(201);
        String workerUrl = JsonPath.read(issueResponse.body(), "$.worker_url");
        assertThat(workerUrl).isNotBlank();

        HttpResponse<String> viewResponse = getJson("/public/worker-links/" + workerUrl, null);
        assertThat(viewResponse.statusCode()).isEqualTo(200);
        assertThat(viewResponse.headers().firstValue("Cache-Control")).contains("no-store");

        HttpResponse<String> uploadResponse = uploadFile(workerUrl, "passport.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));
        assertThat(uploadResponse.statusCode()).isEqualTo(201);
        String uploadId = JsonPath.read(uploadResponse.body(), "$.upload_id");

        HttpResponse<String> uploadFirstResponse = uploadFileWithFixedClientRequestId(
                workerUrl, "passport.pdf", "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8), "fixed-client-request-id"
        );
        assertThat(uploadFirstResponse.statusCode()).isEqualTo(201);
        String firstUploadId = JsonPath.read(uploadFirstResponse.body(), "$.upload_id");

        HttpResponse<String> uploadRetryResponse = uploadFileWithFixedClientRequestId(
                workerUrl, "passport.pdf", "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8), "fixed-client-request-id"
        );
        assertThat(uploadRetryResponse.statusCode()).isEqualTo(201);
        String retryUploadId = JsonPath.read(uploadRetryResponse.body(), "$.upload_id");
        assertThat(retryUploadId).isEqualTo(firstUploadId);

        HttpResponse<String> responseSubmit = postJson(
                "/public/worker-links/" + workerUrl + "/responses",
                """
                {"response_type":"DOCUMENT_SUBMITTED","upload_ids":["%s"],"idempotency_key":"key-1"}
                """.formatted(uploadId),
                null
        );
        assertThat(responseSubmit.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(responseSubmit.body(), "$.response_id")).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT conversation_status FROM worker_link WHERE task_id = ?",
                String.class,
                UUID.fromString(taskId)
        )).isEqualTo("NEEDS_FOLLOWUP");

        HttpResponse<String> activitiesResponse = getJson("/api/v1/tasks/" + taskId + "/activities", hrToken);
        assertThat(activitiesResponse.statusCode()).isEqualTo(200);
        assertThat(activitiesResponse.body()).contains("WORKER_LINK_RESPONSE_SUBMITTED");
    }

    @Test
    void hrCanListAndMarkWorkerResponsesReviewed() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "응답조회테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String workerUrl = issueWorkerLink(hrToken, taskId, "response-management-key");

        HttpResponse<String> submitResponse = postJson(
                "/public/worker-links/" + workerUrl + "/responses",
                """
                {"response_type":"QUESTION","message":"여권의 어느 면을 제출하나요?","idempotency_key":"question-key"}
                """,
                null
        );
        assertThat(submitResponse.statusCode()).isEqualTo(201);

        HttpResponse<String> listResponse = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses?page=0&size=20",
                hrToken
        );
        assertThat(listResponse.statusCode()).as("body: %s", listResponse.body()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(listResponse.body(), "$.total_elements")).isEqualTo(1);
        assertThat(JsonPath.<String>read(listResponse.body(), "$.items[0].response_type"))
                .isEqualTo("QUESTION");
        assertThat(JsonPath.<String>read(listResponse.body(), "$.items[0].message"))
                .isEqualTo("여권의 어느 면을 제출하나요?");
        assertThat(JsonPath.<Boolean>read(listResponse.body(), "$.items[0].unread")).isTrue();

        HttpResponse<String> readResponse = postWithoutBody(
                "/api/v1/tasks/" + taskId + "/worker-responses/read",
                hrToken
        );
        assertThat(readResponse.statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT conversation_status FROM worker_link WHERE task_id = ?",
                String.class,
                UUID.fromString(taskId)
        )).isEqualTo("REOPENED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_RESPONSES_REVIEWED'",
                Integer.class,
                UUID.fromString(taskId)
        )).isEqualTo(1);

        HttpResponse<String> reviewedListResponse = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses",
                hrToken
        );
        assertThat(JsonPath.<Boolean>read(reviewedListResponse.body(), "$.items[0].unread")).isFalse();

        HttpResponse<String> repeatedReadResponse = postWithoutBody(
                "/api/v1/tasks/" + taskId + "/worker-responses/read",
                hrToken
        );
        assertThat(repeatedReadResponse.statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_RESPONSES_REVIEWED'",
                Integer.class,
                UUID.fromString(taskId)
        )).isEqualTo(1);
    }

    @Test
    void workerResponsesAreHiddenFromOtherCompany() throws Exception {
        String hrTokenA = accessToken(login(HR_A_EMAIL));
        String hrTokenB = accessToken(login(HR_B_EMAIL));
        String workerId = registerWorker(hrTokenA, "응답격리테스트근로자");
        String taskId = createApprovedTask(hrTokenA, workerId);

        HttpResponse<String> listResponse = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses",
                hrTokenB
        );
        assertThat(listResponse.statusCode()).isEqualTo(404);

        HttpResponse<String> readResponse = postWithoutBody(
                "/api/v1/tasks/" + taskId + "/worker-responses/read",
                hrTokenB
        );
        assertThat(readResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void hrCanQueryAndMarkWorkerLinkSentIdempotently() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "링크전달테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                "delivery-issue-key"
        );
        assertThat(issueResponse.statusCode()).isEqualTo(201);
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        assertThat(JsonPath.<String>read(issueResponse.body(), "$.delivery_status"))
                .isEqualTo("NOT_SENT");
        assertThat(JsonPath.<Object>read(issueResponse.body(), "$.sent_at")).isNull();

        HttpResponse<String> beforeSent = getJson(
                "/api/v1/tasks/" + taskId + "/worker-link",
                hrToken
        );
        assertThat(beforeSent.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(beforeSent.body(), "$.worker_link_id"))
                .isEqualTo(workerLinkId);
        assertThat(JsonPath.<String>read(beforeSent.body(), "$.delivery_status"))
                .isEqualTo("NOT_SENT");

        HttpResponse<String> sent = postWithoutBodyWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken,
                "delivery-sent-key"
        );
        assertThat(sent.statusCode()).as("sent response body: %s", sent.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(sent.body(), "$.delivery_status")).isEqualTo("SENT");
        assertThat(JsonPath.<String>read(sent.body(), "$.sent_at")).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sent_by FROM worker_link WHERE worker_link_id = ?",
                UUID.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo(HR_A);

        HttpResponse<String> sameKeyRetry = postWithoutBodyWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken,
                "delivery-sent-key"
        );
        HttpResponse<String> differentKeyRetry = postWithoutBodyWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken,
                "delivery-sent-key-2"
        );
        assertThat(sameKeyRetry.statusCode()).isEqualTo(200);
        assertThat(differentKeyRetry.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(sameKeyRetry.body(), "$.sent_at"))
                .isEqualTo(JsonPath.read(sent.body(), "$.sent_at"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_SENT'",
                Integer.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo(1);
    }

    @Test
    void deliveryEndpointsHideOtherCompanyAndRejectViewer() throws Exception {
        String hrTokenA = accessToken(login(HR_A_EMAIL));
        String hrTokenB = accessToken(login(HR_B_EMAIL));
        String viewerTokenA = accessToken(login(VIEWER_A_EMAIL));
        String workerId = registerWorker(hrTokenA, "링크권한테스트근로자");
        String taskId = createApprovedTask(hrTokenA, workerId);
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrTokenA,
                "delivery-security-issue-key"
        );
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");

        assertThat(getJson("/api/v1/tasks/" + taskId + "/worker-link", hrTokenB).statusCode())
                .isEqualTo(404);
        assertThat(postWithoutBodyWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrTokenB,
                "delivery-other-company-key"
        ).statusCode()).isEqualTo(404);
        assertThat(getJson("/api/v1/tasks/" + taskId + "/worker-link", viewerTokenA).statusCode())
                .isEqualTo(403);
        assertThat(postWithoutBodyWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                viewerTokenA,
                "delivery-viewer-key"
        ).statusCode()).isEqualTo(403);
    }

    @Test
    void missingIdempotencyKeyDoesNotMarkWorkerLinkSent() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "링크헤더테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                "delivery-header-issue-key"
        );
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");

        HttpResponse<String> response = postWithoutBody(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM worker_link WHERE worker_link_id = ?",
                String.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo("NOT_SENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_SENT'",
                Integer.class,
                UUID.fromString(workerLinkId)
        )).isZero();
    }

    @Test
    void documentsWorkerLinkDeliveryEndpointsInOpenApi() throws Exception {
        HttpResponse<String> response = getJson("/v3/api-docs", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/tasks/{taskId}/worker-link'].get.operationId"
        )).isEqualTo("getTaskWorkerLinkDelivery");
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/worker-links/{workerLinkId}/sent'].post.operationId"
        )).isEqualTo("markWorkerLinkSent");
    }

    @Test
    void issueRejectsUnapprovedTask() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "미승인테스트근로자");
        String taskId = createUnapprovedTask(hrToken, workerId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                "unapproved-issue-key"
        );

        assertThat(issueResponse.statusCode()).isEqualTo(422);
    }

    @Test
    void issueRejectsOtherCompanyTask() throws Exception {
        String hrTokenA = accessToken(login(HR_A_EMAIL));
        String hrTokenB = accessToken(login(HR_B_EMAIL));
        String workerId = registerWorker(hrTokenA, "타사업장테스트근로자");
        String taskId = createApprovedTask(hrTokenA, workerId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrTokenB,
                "othercompany-issue-key"
        );

        assertThat(issueResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void viewReturns410ForNonExistentToken() throws Exception {
        HttpResponse<String> viewResponse = getJson("/public/worker-links/nonexistenttoken12345", null);
        assertThat(viewResponse.statusCode()).isEqualTo(410);
    }
    @Test
    void documentsEndpointAllowsIdempotencyKeyHeaderInCors() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/public/worker-links/test-token/documents"))
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Idempotency-Key")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers"))
                .hasValueSatisfying(headers -> assertThat(headers).contains("Idempotency-Key"));
    }

    private String registerWorker(String token, String displayName) throws Exception {
        String body = """
                {"display_name":"%s"}
                """.formatted(displayName);
        HttpResponse<String> response = postJson("/api/v1/workers", body, token);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    private String createApprovedTask(String token, String workerId) throws Exception {
        String taskId = createUnapprovedTask(token, workerId);
        completeRequiredChecklistItems(taskId, token);

        String approvalBody = """
                {
                  "expected_version":0,
                  "ai_snapshot":{"intent":"EXPIRY_RENEWAL","confidence":0.94},
                  "hr_snapshot":{"worker_id":"%s","contract_end_date":"2027-08-31","monthly_wage":2500000},
                  "changed_fields":["monthly_wage"],
                  "source_versions":{"agent_version":"agent-1","workflow_catalog_version":"2026.07"}
                }
                """.formatted(workerId);
        HttpResponse<String> approvalRequest = postJson(
                "/api/v1/tasks/" + taskId + "/approval-requests", approvalBody, token
        );
        assertThat(approvalRequest.statusCode()).as("approval response body: %s", approvalRequest.body()).isEqualTo(201);

        HttpResponse<String> approve = postJson(
                "/api/v1/tasks/" + taskId + "/approve",
                """
                {"expected_version":1,"reason":"테스트 승인"}
                """,
                token
        );
        assertThat(approve.statusCode()).as("approve response body: %s", approve.body()).isEqualTo(200);
        return taskId;
    }

    private String issueWorkerLink(String token, String taskId, String idempotencyKey) throws Exception {
        HttpResponse<String> response = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                token,
                idempotencyKey
        );
        assertThat(response.statusCode()).as("issue response body: %s", response.body()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_url");
    }

    private void completeRequiredChecklistItems(String taskId, String token) throws Exception {
        HttpResponse<String> detail = getJson("/api/v1/tasks/" + taskId, token);
        assertThat(detail.statusCode()).isEqualTo(200);

        List<Map<String, Object>> checklistItems = JsonPath.read(detail.body(), "$.checklist_items");
        for (Map<String, Object> item : checklistItems) {
            boolean required = (boolean) item.get("required");
            if (!required) {
                continue;
            }
            String itemId = (String) item.get("checklist_item_id");
            int itemVersion = (int) item.get("version");
            int taskVersion = ((Number) JsonPath.read(detail.body(), "$.version")).intValue();

            HttpResponse<String> patchResponse = patchJson(
                    "/api/v1/tasks/" + taskId + "/checklist-items/" + itemId,
                    """
                    {"completed":true,"expected_version":%d,"expected_task_version":%d}
                    """.formatted(itemVersion, taskVersion),
                    token
            );
            assertThat(patchResponse.statusCode())
                    .as("checklist patch response: %s", patchResponse.body())
                    .isEqualTo(200);
        }
    }

    private String createUnapprovedTask(String token, String workerId) throws Exception {
        String body = """
                {
                  "worker_id":"%s",
                  "task_type":"RECONTRACT",
                  "workflow_id":"WF-CON-001",
                  "title":"재계약 준비",
                  "description":"기존 조건 확인",
                  "due_date":"2026-08-20",
                  "business_data":{"monthly_wage":2500000}
                }
                """.formatted(workerId);
        HttpResponse<String> response = postJson("/api/v1/tasks", body, token);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.task_id");
    }

    private HttpResponse<String> uploadFile(String token, String filename, String mimeType, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, "file", filename, mimeType, content);
        writeFieldPart(out, "clientRequestId", UUID.randomUUID().toString());
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/public/worker-links/" + token + "/documents"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uploadFileWithFixedClientRequestId(
            String token, String filename, String mimeType, byte[] content, String clientRequestId
    ) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, "file", filename, mimeType, content);
        writeFieldPart(out, "clientRequestId", clientRequestId);
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/public/worker-links/" + token + "/documents"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void writePart(ByteArrayOutputStream out, String name, String filename, String mimeType, byte[] content)
            throws IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFieldPart(ByteArrayOutputStream out, String name, String value) throws IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                companyId, name
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
                userId, companyId, email, email, passwordHash, role
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

    private HttpResponse<String> getJson(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJsonWithIdempotencyKey(String path, String body, String token, String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutBody(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutBodyWithIdempotencyKey(
            String path,
            String token,
            String idempotencyKey
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patchJson(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
