package com.fowoco.server.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
class DocumentSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("91000000-0000-0000-0000-000000000002");
    private static final String HR_A_EMAIL = "hr.doc.a@example.com";
    private static final String HR_B_EMAIL = "hr.doc.b@example.com";
    private static final String PASSWORD = "Test-password-1!";
    private static final String BOUNDARY = "FowocoDocTestBoundary1234";

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
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash);
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash);
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.update("DELETE FROM document_request_draft_type");
        jdbcTemplate.update("DELETE FROM document_request_draft");
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
        jdbcTemplate.update("DELETE FROM document_request_draft_type");
        jdbcTemplate.update("DELETE FROM document_request_draft");
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
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void readinessReflectsMissingThenAvailableAfterDocumentLinked() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(token, "준비도테스트근로자");
        String taskId = createTask(token, workerId);

        HttpResponse<String> beforeResponse = getJson("/api/v1/tasks/" + taskId + "/document-readiness", token);
        assertThat(beforeResponse.statusCode()).isEqualTo(200);
        List<String> missingBefore = JsonPath.read(beforeResponse.body(), "$.missing");
        assertThat(missingBefore).containsExactlyInAnyOrder("CONTRACT", "PERMIT");
        assertThat(JsonPath.<Boolean>read(beforeResponse.body(), "$.completion_blocked")).isTrue();

        String fileId = uploadFile(token, "contract.pdf", "application/pdf", "contract content".getBytes(StandardCharsets.UTF_8));
        String documentId = registerWorkerDocument(token, workerId, "CONTRACT");
        linkFileToDocument(token, workerId, documentId, fileId, 0);

        String fileId2 = uploadFile(token, "permit.pdf", "application/pdf", "permit content".getBytes(StandardCharsets.UTF_8));
        String documentId2 = registerWorkerDocument(token, workerId, "PERMIT");
        linkFileToDocument(token, workerId, documentId2, fileId2, 0);

        HttpResponse<String> afterResponse = getJson("/api/v1/tasks/" + taskId + "/document-readiness", token);
        List<String> availableAfter = JsonPath.read(afterResponse.body(), "$.available");
        assertThat(availableAfter).containsExactlyInAnyOrder("CONTRACT", "PERMIT");
        assertThat(JsonPath.<Boolean>read(afterResponse.body(), "$.completion_blocked")).isFalse();
    }

    @Test
    void readinessReturnsNotFoundForOtherCompanyTask() throws Exception {
        String tokenA = accessToken(login(HR_A_EMAIL));
        String tokenB = accessToken(login(HR_B_EMAIL));
        String workerId = registerWorker(tokenA, "격리테스트근로자");
        String taskId = createTask(tokenA, workerId);

        HttpResponse<String> response = getJson("/api/v1/tasks/" + taskId + "/document-readiness", tokenB);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void listDocumentsIncludesWorkerDisplayNameAndFiltersByWorker() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(token, "목록조회테스트");
        registerWorkerDocument(token, workerId, "CONTRACT");

        HttpResponse<String> response = getJson("/api/v1/documents?worker_id=" + workerId, token);

        assertThat(response.statusCode()).isEqualTo(200);
        List<?> items = JsonPath.read(response.body(), "$.items");
        assertThat(items).hasSize(1);
        assertThat(JsonPath.<String>read(response.body(), "$.items[0].display_name"))
                .isEqualTo("목록조회테스트");
    }

    @Test
    void documentRequestDraftCreatesThenUpdatesThenRejectsStaleVersion() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(token, "초안테스트근로자");
        String taskId = createTask(token, workerId);
        String path = "/api/v1/tasks/" + taskId + "/document-request-draft";

        String createBody = """
                {"language":"vi","document_types":["CONTRACT"],"message":"계약서를 제출해 주세요.","expected_version":0}
                """;
        HttpResponse<String> createResponse = putJson(path, createBody, token);
        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(createResponse.body(), "$.version").longValue()).isZero();
        assertThat(JsonPath.<String>read(createResponse.body(), "$.review_status")).isEqualTo("DRAFT");

        String updateBody = """
                {"language":"ko","document_types":["CONTRACT","PERMIT"],"message":"수정된 안내","expected_version":0}
                """;
        HttpResponse<String> updateResponse = putJson(path, updateBody, token);
        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(updateResponse.body(), "$.version").longValue()).isEqualTo(1);

        HttpResponse<String> staleResponse = putJson(path, updateBody, token);
        assertThat(staleResponse.statusCode()).isEqualTo(409);
    }

    @Test
    void documentRequestDraftReturnsNotFoundForNonExistentTask() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        String path = "/api/v1/tasks/" + UUID.randomUUID() + "/document-request-draft";

        String body = """
                {"language":"vi","document_types":["CONTRACT"],"expected_version":0}
                """;
        HttpResponse<String> response = putJson(path, body, token);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private String registerWorker(String token, String displayName) throws Exception {
        String body = """
                {"display_name":"%s"}
                """.formatted(displayName);
        HttpResponse<String> response = postJson("/api/v1/workers", body, token);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    private String createTask(String token, String workerId) throws Exception {
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

    private String registerWorkerDocument(String token, String workerId, String documentType) throws Exception {
        String body = """
                {"document_type":"%s","submission_status":"SUBMITTED"}
                """.formatted(documentType);
        HttpResponse<String> response = postJson("/api/v1/workers/" + workerId + "/documents", body, token);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_document_id");
    }

    private void linkFileToDocument(String token, String workerId, String documentId, String fileId, long expectedVersion)
            throws Exception {
        String body = """
                {"file_id":"%s","expected_version":%d}
                """.formatted(fileId, expectedVersion);
        HttpResponse<String> response = patchJson(
                "/api/v1/workers/" + workerId + "/documents/" + documentId, body, token
        );
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String uploadFile(String token, String filename, String mimeType, byte[] content) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        writePart(out, "file", filename, mimeType, content);
        writeFieldPart(out, "purpose", "WORKER_DOCUMENT");
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/files"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.file_id");
    }

    private void writePart(
            java.io.ByteArrayOutputStream out, String name, String filename, String mimeType, byte[] content
    ) throws java.io.IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
                ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFieldPart(java.io.ByteArrayOutputStream out, String name, String value) throws java.io.IOException {
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
                companyId,
                name
        );
    }

    private void insertUser(UUID userId, UUID companyId, String email, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                userId,
                companyId,
                email,
                email,
                passwordHash
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
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String token) throws Exception {
        return sendJson(path, body, token, "POST");
    }

    private HttpResponse<String> patchJson(String path, String body, String token) throws Exception {
        return sendJson(path, body, token, "PATCH");
    }

    private HttpResponse<String> putJson(String path, String body, String token) throws Exception {
        return sendJson(path, body, token, "PUT");
    }

    private HttpResponse<String> sendJson(String path, String body, String token, String method) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
