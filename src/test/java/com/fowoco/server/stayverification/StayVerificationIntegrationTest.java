package com.fowoco.server.stayverification;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
class StayVerificationIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID HR_A = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_A = UUID.fromString("a3000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_B = UUID.fromString("b3000000-0000-0000-0000-000000000001");
    private static final String EMAIL_A = "hr.stay.a@example.com";
    private static final String EMAIL_B = "hr.stay.b@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String tokenA;
    private String tokenB;

    @BeforeAll
    void seedAccounts() throws Exception {
        insertCompany(COMPANY_A, "체류확인 사업장 A");
        insertCompany(COMPANY_B, "체류확인 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, EMAIL_A, passwordHash);
        insertUser(HR_B, COMPANY_B, EMAIL_B, passwordHash);
        tokenA = accessToken(login(EMAIL_A));
        tokenB = accessToken(login(EMAIL_B));
    }

    @BeforeEach
    void resetCasesAndWorkers() {
        jdbcTemplate.update("DELETE FROM audit_event WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM stay_verification_case WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM worker_document WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM worker WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        insertWorker(WORKER_A, COMPANY_A, "응웬반A", "ACTIVE", LocalDate.now().minusDays(3));
        insertWorker(WORKER_B, COMPANY_B, "타사업장근로자", "ACTIVE", LocalDate.now().minusDays(5));
    }

    @AfterEach
    void removeCasesAndWorkers() {
        jdbcTemplate.update("DELETE FROM audit_event WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM stay_verification_case WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM worker_document WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM worker WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
    }

    @AfterAll
    void removeAccounts() {
        jdbcTemplate.update("DELETE FROM refresh_token WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM user_account WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM company WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
    }

    @Test
    void scanIsIdempotentAndTenantScoped() throws Exception {
        HttpResponse<String> first = post("/api/v1/stay-verifications/scan", "{}", tokenA);
        HttpResponse<String> second = post("/api/v1/stay-verifications/scan", "{}", tokenA);
        HttpResponse<String> companyAList = get("/api/v1/stay-verifications", tokenA);
        HttpResponse<String> companyBList = get("/api/v1/stay-verifications", tokenB);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(first.body(), "$.created_count").intValue()).isEqualTo(1);
        assertThat(JsonPath.<Number>read(second.body(), "$.created_count").intValue()).isZero();
        assertThat(JsonPath.<List<?>>read(companyAList.body(), "$[*]")).hasSize(1);
        assertThat(JsonPath.<String>read(companyAList.body(), "$[0].verification_status"))
                .isEqualTo("UNKNOWN");
        assertThat(JsonPath.<List<?>>read(companyBList.body(), "$[*]")).isEmpty();
    }

    @Test
    void approvedStatusUpdatesStayExpiryAndWritesAudit() throws Exception {
        String verificationId = createVerificationAndGetId();
        String newExpiry = LocalDate.now().plusYears(1).toString();

        HttpResponse<String> response = patch(
                "/api/v1/stay-verifications/" + verificationId,
                """
                {
                  "status": "APPROVED",
                  "new_stay_expiry_date": "%s",
                  "official_consultation_note": "하이코리아 승인 결과를 확인함",
                  "expected_version": 0
                }
                """.formatted(newExpiry),
                tokenA
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.verification_status")).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stay_expiry_date FROM worker WHERE worker_id = ?",
                LocalDate.class,
                WORKER_A
        )).isEqualTo(LocalDate.parse(newExpiry));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'STAY_VERIFICATION_STATUS_UPDATED'",
                Integer.class,
                UUID.fromString(verificationId)
        )).isEqualTo(1);
    }

    @Test
    void approvalWithoutEvidenceIsRejectedAndDoesNotChangeWorker() throws Exception {
        String verificationId = createVerificationAndGetId();
        LocalDate originalExpiry = LocalDate.now().minusDays(3);

        HttpResponse<String> response = patch(
                "/api/v1/stay-verifications/" + verificationId,
                """
                {
                  "status": "APPROVED",
                  "new_stay_expiry_date": "%s",
                  "expected_version": 0
                }
                """.formatted(LocalDate.now().plusYears(1)),
                tokenA
        );

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("STAY_VERIFICATION_EVIDENCE_REQUIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stay_expiry_date FROM worker WHERE worker_id = ?",
                LocalDate.class,
                WORKER_A
        )).isEqualTo(originalExpiry);
    }

    @Test
    void employmentEndedOnlySuggestsEmploymentChangeAfterHrConfirmation() throws Exception {
        String verificationId = createVerificationAndGetId();

        HttpResponse<String> response = patch(
                "/api/v1/stay-verifications/" + verificationId,
                """
                {
                  "status": "EMPLOYMENT_ENDED",
                  "official_consultation_note": "HR이 근로관계 종료 사실을 확인함",
                  "employment_end_confirmed_at": "2026-08-17T04:00:00Z",
                  "expected_version": 0
                }
                """,
                tokenA
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(response.body(), "$.employment_change_candidate_available"))
                .isTrue();
        assertThat(JsonPath.<String>read(response.body(), "$.suggested_workflow_id"))
                .isEqualTo("WF-CHG-001");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT work_status FROM worker WHERE worker_id = ?",
                String.class,
                WORKER_A
        )).isEqualTo("ACTIVE");
    }

    private String createVerificationAndGetId() throws Exception {
        assertThat(post("/api/v1/stay-verifications/scan", "{}", tokenA).statusCode()).isEqualTo(200);
        HttpResponse<String> list = get("/api/v1/stay-verifications", tokenA);
        return JsonPath.read(list.body(), "$[0].stay_verification_id");
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

    private void insertWorker(
            UUID workerId,
            UUID companyId,
            String displayName,
            String status,
            LocalDate stayExpiryDate
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, work_status, stay_expiry_date,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                workerId,
                companyId,
                displayName,
                status,
                stayExpiryDate
        );
    }

    private HttpResponse<String> login(String email) throws Exception {
        return post(
                "/api/v1/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD),
                null
        );
    }

    private String accessToken(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        return send(path, body, token, "POST");
    }

    private HttpResponse<String> patch(String path, String body, String token) throws Exception {
        return send(path, body, token, "PATCH");
    }

    private HttpResponse<String> send(String path, String body, String token, String method) throws Exception {
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
