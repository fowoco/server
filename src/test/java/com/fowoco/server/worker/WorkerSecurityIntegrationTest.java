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

/*
    1. 등록→조회 정상 흐름 + 민감정보 미포함 확인
    2. PATCH가 보낸 필드만 바꾸고 version이 0→1로 오르는지
    3. 오래된 expected_version으로 PATCH하면 409
    4. 타 사업장 근로자 조회 시 404
    5. 계약기간 역전 시 400 VALIDATION_FAILED
    6. 목록 조회 페이지네이션
    7. 목록 status 필터
    8. 목록 타 사업장 격리
    9. size 범위 벗어나면 400
    10. VIEWER는 쓰기 403, 조회는 가능
*/

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkerSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID VIEWER_A = UUID.fromString("32000000-0000-0000-0000-000000000001");
    private static final String HR_A_EMAIL = "hr.worker.a@example.com";
    private static final String HR_B_EMAIL = "hr.worker.b@example.com";
    private static final String VIEWER_A_EMAIL = "viewer.worker.a@example.com";
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
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash, "HR");
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash, "HR");
        insertUser(VIEWER_A, COMPANY_A, VIEWER_A_EMAIL, passwordHash, "VIEWER");
    }

    @BeforeEach
    void resetWorkerState() {
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
    }

    @Test
    void registerThenGetReturnsTheSameWorkerWithoutSensitiveFields() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        String registerBody = """
                {
                  "display_name": "응우웬반A",
                  "nationality_code": "VN",
                  "preferred_language": "vi",
                  "stay_expiry_date": "2027-03-01",
                  "contract_start_date": "2026-01-01",
                  "contract_end_date": "2027-12-31"
                }
                """;
        HttpResponse<String> registerResponse = postJson("/api/v1/workers", registerBody, accessToken);

        assertThat(registerResponse.statusCode()).isEqualTo(201);
        String workerId = JsonPath.read(registerResponse.body(), "$.worker_id");
        assertThat(JsonPath.<String>read(registerResponse.body(), "$.display_name")).isEqualTo("응우웬반A");
        assertThat(JsonPath.<String>read(registerResponse.body(), "$.work_status")).isEqualTo("ACTIVE");
        assertThat(JsonPath.<Number>read(registerResponse.body(), "$.version").longValue()).isZero();
        assertThat(registerResponse.body())
                .doesNotContain("legal_name")
                .doesNotContain("passport")
                .doesNotContain("phone");

        HttpResponse<String> getResponse = authorizedGet("/api/v1/workers/" + workerId, accessToken);

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(getResponse.body(), "$.worker_id")).isEqualTo(workerId);
        assertThat(JsonPath.<String>read(getResponse.body(), "$.nationality_code")).isEqualTo("VN");
    }

    @Test
    void patchAppliesOnlyProvidedFieldsAndBumpsVersion() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(accessToken, "패치테스트근로자");

        String patchBody = """
                {
                  "preferred_language": "en",
                  "expected_version": 0
                }
                """;
        HttpResponse<String> patchResponse = patchJson(
                "/api/v1/workers/" + workerId,
                patchBody,
                accessToken
        );

        assertThat(patchResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(patchResponse.body(), "$.preferred_language")).isEqualTo("en");
        assertThat(JsonPath.<String>read(patchResponse.body(), "$.display_name")).isEqualTo("패치테스트근로자");
        assertThat(JsonPath.<Number>read(patchResponse.body(), "$.version").longValue()).isEqualTo(1);
    }

    @Test
    void patchWithStaleExpectedVersionReturnsConflict() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(accessToken, "버전충돌테스트");

        String firstPatchBody = """
                {"preferred_language": "en", "expected_version": 0}
                """;
        HttpResponse<String> firstPatch = patchJson(
                "/api/v1/workers/" + workerId,
                firstPatchBody,
                accessToken
        );
        assertThat(firstPatch.statusCode()).isEqualTo(200);

        String staleBody = """
                {"preferred_language": "ko", "expected_version": 0}
                """;
        HttpResponse<String> staleResponse = patchJson(
                "/api/v1/workers/" + workerId,
                staleBody,
                accessToken
        );

        assertThat(staleResponse.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(staleResponse.body(), "$.code"))
                .isEqualTo("WORKER_VERSION_CONFLICT");
    }

    @Test
    void workerFromAnotherCompanyIsReturnedAsNotFound() throws Exception {
        String companyAToken = accessToken(login(HR_A_EMAIL));
        String companyBToken = accessToken(login(HR_B_EMAIL));
        String workerId = registerWorker(companyAToken, "타사업장격리테스트");

        HttpResponse<String> hiddenResponse = authorizedGet("/api/v1/workers/" + workerId, companyBToken);

        assertThat(hiddenResponse.statusCode()).isEqualTo(404);
        assertThat(JsonPath.<String>read(hiddenResponse.body(), "$.code")).isEqualTo("WORKER_NOT_FOUND");
    }

    @Test
    void invalidContractPeriodIsRejectedWithValidationFailed() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        String invalidBody = """
                {
                  "display_name": "잘못된계약기간",
                  "contract_start_date": "2027-01-01",
                  "contract_end_date": "2026-01-01"
                }
                """;
        HttpResponse<String> response = postJson("/api/v1/workers", invalidBody, accessToken);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void listWorkersReturnsPagedItemsWithinCompany() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        registerWorker(accessToken, "목록테스트1");
        registerWorker(accessToken, "목록테스트2");
        registerWorker(accessToken, "목록테스트3");

        HttpResponse<String> firstPage = authorizedGet(
                "/api/v1/workers?page=0&size=2",
                accessToken
        );

        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(firstPage.body(), "$.items")).hasSize(2);
        assertThat(JsonPath.<Number>read(firstPage.body(), "$.page").intValue()).isZero();
        assertThat(JsonPath.<Number>read(firstPage.body(), "$.size").intValue()).isEqualTo(2);
        assertThat(JsonPath.<Number>read(firstPage.body(), "$.total_elements").longValue()).isEqualTo(3);

        HttpResponse<String> secondPage = authorizedGet(
                "/api/v1/workers?page=1&size=2",
                accessToken
        );

        assertThat(secondPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(secondPage.body(), "$.items")).hasSize(1);
    }

    @Test
    void listWorkersFiltersByStatus() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));
        String activeWorkerId = registerWorker(accessToken, "활성근로자");
        String onLeaveWorkerId = registerWorker(accessToken, "휴직근로자");
        patchJson(
                "/api/v1/workers/" + onLeaveWorkerId,
                """
                {"work_status": "ON_LEAVE", "expected_version": 0}
                """,
                accessToken
        );

        HttpResponse<String> response = authorizedGet(
                "/api/v1/workers?status=ACTIVE",
                accessToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.items")).hasSize(1);
        assertThat(JsonPath.<String>read(response.body(), "$.items[0].worker_id")).isEqualTo(activeWorkerId);
    }

    @Test
    void listWorkersDoesNotIncludeOtherCompanyWorkers() throws Exception {
        String companyAToken = accessToken(login(HR_A_EMAIL));
        String companyBToken = accessToken(login(HR_B_EMAIL));
        registerWorker(companyAToken, "A사업장근로자");
        registerWorker(companyBToken, "B사업장근로자");

        HttpResponse<String> companyAList = authorizedGet("/api/v1/workers", companyAToken);

        assertThat(companyAList.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(companyAList.body(), "$.items")).hasSize(1);
        assertThat(JsonPath.<String>read(companyAList.body(), "$.items[0].display_name"))
                .isEqualTo("A사업장근로자");
    }

    @Test
    void listWorkersRejectsOutOfRangeSizeWithValidationFailed() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> tooSmall = authorizedGet("/api/v1/workers?size=0", accessToken);
        HttpResponse<String> tooLarge = authorizedGet("/api/v1/workers?size=200", accessToken);

        assertThat(tooSmall.statusCode()).isEqualTo(400);
        assertThat(tooLarge.statusCode()).isEqualTo(400);
    }

    @Test
    void viewerCannotRegisterButCanListAndGet() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "VIEWER조회테스트");
        String viewerToken = accessToken(login(VIEWER_A_EMAIL));

        HttpResponse<String> registerAttempt = postJson(
                "/api/v1/workers",
                """
                {"display_name": "VIEWER등록시도"}
                """,
                viewerToken
        );
        HttpResponse<String> listAttempt = authorizedGet("/api/v1/workers", viewerToken);
        HttpResponse<String> getAttempt = authorizedGet("/api/v1/workers/" + workerId, viewerToken);

        assertThat(registerAttempt.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(registerAttempt.body(), "$.code")).isEqualTo("ACCESS_DENIED");
        assertThat(listAttempt.statusCode()).isEqualTo(200);
        assertThat(getAttempt.statusCode()).isEqualTo(200);
    }

    private String registerWorker(String accessToken, String displayName) throws Exception {
        String body = """
                {"display_name": "%s"}
                """.formatted(displayName);
        HttpResponse<String> response = postJson("/api/v1/workers", body, accessToken);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
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
