package com.fowoco.server.worker;

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
class WorkerDocumentSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("60000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("61000000-0000-0000-0000-000000000002");
    private static final String HR_A_EMAIL = "hr.doc.a@example.com";
    private static final String HR_B_EMAIL = "hr.doc.b@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String workerIdInCompanyA;

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

        insertCompany(COMPANY_A, "사업장 A");
        insertCompany(COMPANY_B, "사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash);
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash);
    }

    @BeforeEach
    void resetWorkerDocumentState() throws Exception {
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        workerIdInCompanyA = registerWorker(accessToken(login(HR_A_EMAIL)), "서류테스트근로자");
    }

    @Test
    void registerDocumentSucceedsWithDeclaredType() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        String body = """
                {
                  "document_type": "PASSPORT_COPY",
                  "submission_status": "SUBMITTED",
                  "expiry_date": "2027-03-01",
                  "destination": "출입국관리사무소",
                  "note": "테스트 메모"
                }
                """;
        HttpResponse<String> response = postJson(
                "/api/v1/workers/" + workerIdInCompanyA + "/documents",
                body,
                accessToken
        );

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(response.body(), "$.document_type")).isEqualTo("PASSPORT_COPY");
        assertThat(JsonPath.<String>read(response.body(), "$.submission_status")).isEqualTo("SUBMITTED");
        assertThat(JsonPath.<String>read(response.body(), "$.worker_id")).isEqualTo(workerIdInCompanyA);
        assertThat(JsonPath.<Number>read(response.body(), "$.version").longValue()).isZero();
    }

    @Test
    void patchAppliesOnlyProvidedFieldsAndBumpsVersion() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String documentId = registerDocument(accessToken, workerIdInCompanyA);

        String patchBody = """
                {"submission_status": "VERIFIED", "expected_version": 0}
                """;
        HttpResponse<String> patchResponse = patchJson(
                "/api/v1/workers/" + workerIdInCompanyA + "/documents/" + documentId,
                patchBody,
                accessToken
        );

        assertThat(patchResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(patchResponse.body(), "$.submission_status")).isEqualTo("VERIFIED");
        assertThat(JsonPath.<String>read(patchResponse.body(), "$.document_type")).isEqualTo("PASSPORT_COPY");
        assertThat(JsonPath.<Number>read(patchResponse.body(), "$.version").longValue()).isEqualTo(1);
    }

    @Test
    void patchWithStaleExpectedVersionReturnsConflict() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String documentId = registerDocument(accessToken, workerIdInCompanyA);
        String path = "/api/v1/workers/" + workerIdInCompanyA + "/documents/" + documentId;

        HttpResponse<String> firstPatch = patchJson(
                path,
                """
                {"submission_status": "VERIFIED", "expected_version": 0}
                """,
                accessToken
        );
        assertThat(firstPatch.statusCode()).isEqualTo(200);

        HttpResponse<String> staleResponse = patchJson(
                path,
                """
                {"submission_status": "MISSING", "expected_version": 0}
                """,
                accessToken
        );

        assertThat(staleResponse.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(staleResponse.body(), "$.code"))
                .isEqualTo("WORKER_DOCUMENT_VERSION_CONFLICT");
    }

    @Test
    void documentFromAnotherCompanyIsReturnedAsNotFoundOnPatch() throws Exception {
        String companyAToken = accessToken(login(HR_A_EMAIL));
        String companyBToken = accessToken(login(HR_B_EMAIL));
        String documentId = registerDocument(companyAToken, workerIdInCompanyA);

        HttpResponse<String> response = patchJson(
                "/api/v1/workers/" + workerIdInCompanyA + "/documents/" + documentId,
                """
                {"submission_status": "VERIFIED", "expected_version": 0}
                """,
                companyBToken
        );

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("WORKER_DOCUMENT_NOT_FOUND");
    }

    @Test
    void registerRejectsUnknownDocumentTypeWithValidationFailed() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        String body = """
                {
                  "document_type": "NOT_A_REAL_TYPE",
                  "submission_status": "MISSING"
                }
                """;
        HttpResponse<String> response = postJson(
                "/api/v1/workers/" + workerIdInCompanyA + "/documents",
                body,
                accessToken
        );

        assertThat(response.statusCode()).isEqualTo(400);
    }

    private String registerWorker(String accessToken, String displayName) throws Exception {
        String body = """
                {"display_name": "%s"}
                """.formatted(displayName);
        HttpResponse<String> response = postJson("/api/v1/workers", body, accessToken);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    private String registerDocument(String accessToken, String workerId) throws Exception {
        String body = """
                {"document_type": "PASSPORT_COPY", "submission_status": "MISSING"}
                """;
        HttpResponse<String> response = postJson(
                "/api/v1/workers/" + workerId + "/documents",
                body,
                accessToken
        );
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_document_id");
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

    private HttpResponse<String> postJson(String path, String body, String accessToken) throws Exception {
        return sendJson(path, body, accessToken, "POST");
    }

    private HttpResponse<String> patchJson(String path, String body, String accessToken) throws Exception {
        return sendJson(path, body, accessToken, "PATCH");
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
