package com.fowoco.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.auth.infrastructure.security.RefreshTokenHasher;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthSecurityIntegrationTest.ProbeConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID VIEWER_A = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID HR_A = UUID.fromString("12000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_B = UUID.fromString("21000000-0000-0000-0000-000000000002");
    private static final String VIEWER_A_EMAIL = "viewer.a@example.com";
    private static final String HR_A_EMAIL = "hr.a@example.com";
    private static final String VIEWER_B_EMAIL = "viewer.b@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedCompaniesAndUsers() {
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "사업장 A");
        insertCompany(COMPANY_B, "사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(VIEWER_A, COMPANY_A, VIEWER_A_EMAIL, passwordHash, "VIEWER");
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash, "HR");
        insertUser(VIEWER_B, COMPANY_B, VIEWER_B_EMAIL, passwordHash, "VIEWER");
    }

    @BeforeEach
    void clearRefreshTokens() {
        jdbcTemplate.update("DELETE FROM refresh_token");
    }

    @Test
    void loginTokenCreatesActorContextAndStoresOnlyRefreshTokenHash() throws Exception {
        HttpResponse<String> loginResponse = login(VIEWER_A_EMAIL, PASSWORD);

        assertThat(loginResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.user_id"))
                .isEqualTo(VIEWER_A.toString());
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.company_id"))
                .isEqualTo(COMPANY_A.toString());
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.role")).isEqualTo("VIEWER");
        assertThat(loginResponse.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(loginResponse.headers().firstValue(HttpHeaders.PRAGMA)).contains("no-cache");

        String setCookie = loginResponse.headers().firstValue(HttpHeaders.SET_COOKIE).orElseThrow();
        assertThat(setCookie)
                .contains("fowoco_refresh_token=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        String rawRefreshToken = cookieValue(setCookie);
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_token WHERE user_id = ?",
                String.class,
                VIEWER_A
        );
        assertThat(storedHash)
                .isNotEqualTo(rawRefreshToken)
                .isEqualTo(refreshTokenHasher.hash(rawRefreshToken));
        assertThat(loginResponse.body()).doesNotContain(rawRefreshToken);

        String accessToken = JsonPath.read(loginResponse.body(), "$.access_token");
        HttpResponse<String> meResponse = authorizedGet("/api/v1/auth/me", accessToken);
        assertThat(meResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(meResponse.body(), "$.user_id")).isEqualTo(VIEWER_A.toString());
        assertThat(JsonPath.<String>read(meResponse.body(), "$.company_id")).isEqualTo(COMPANY_A.toString());
        assertThat(JsonPath.<String>read(meResponse.body(), "$.roles[0]")).isEqualTo("VIEWER");
    }

    @Test
    void viewerCannotWriteButHrCanWrite() throws Exception {
        String viewerToken = accessToken(login(VIEWER_A_EMAIL, PASSWORD));
        String hrToken = accessToken(login(HR_A_EMAIL, PASSWORD));

        HttpResponse<String> viewerResponse = authorizedPost("/api/v1/security-probe/write", viewerToken);
        HttpResponse<String> hrResponse = authorizedPost("/api/v1/security-probe/write", hrToken);

        assertThat(viewerResponse.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(viewerResponse.body(), "$.code")).isEqualTo("ACCESS_DENIED");
        assertThat(hrResponse.statusCode()).isEqualTo(204);
    }

    @Test
    void resourceFromAnotherCompanyIsReturnedAsNotFound() throws Exception {
        String companyAToken = accessToken(login(VIEWER_A_EMAIL, PASSWORD));
        String companyBToken = accessToken(login(VIEWER_B_EMAIL, PASSWORD));

        assertThat(authorizedGet(companyProbePath(COMPANY_A), companyAToken).statusCode()).isEqualTo(204);
        HttpResponse<String> hiddenResponse = authorizedGet(companyProbePath(COMPANY_B), companyAToken);
        assertThat(hiddenResponse.statusCode()).isEqualTo(404);
        assertThat(JsonPath.<String>read(hiddenResponse.body(), "$.code"))
                .isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(authorizedGet(companyProbePath(COMPANY_B), companyBToken).statusCode()).isEqualTo(204);
    }

    @Test
    void clientCannotChooseCompanyDuringLogin() throws Exception {
        String body = """
                {
                  "email": "%s",
                  "password": "%s",
                  "company_id": "%s"
                }
                """.formatted(VIEWER_A_EMAIL, PASSWORD, COMPANY_B);

        HttpResponse<String> response = postJson("/api/v1/auth/login", body, null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INVALID_REQUEST");
        assertThat(response.headers().firstValue(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(refreshTokenCount()).isZero();
    }

    @Test
    void unknownEmailAndWrongPasswordUseTheSameResponse() throws Exception {
        HttpResponse<String> unknownEmail = login("unknown@example.com", PASSWORD);
        HttpResponse<String> wrongPassword = login(VIEWER_A_EMAIL, "Wrong-password-1!");

        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(unknownEmail.body(), "$.code"))
                .isEqualTo(JsonPath.<String>read(wrongPassword.body(), "$.code"))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(JsonPath.<String>read(unknownEmail.body(), "$.message"))
                .isEqualTo(JsonPath.<String>read(wrongPassword.body(), "$.message"));
        assertThat(unknownEmail.headers().firstValue(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(wrongPassword.headers().firstValue(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(refreshTokenCount()).isZero();
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        String accessToken = accessToken(login(VIEWER_A_EMAIL, PASSWORD));
        String tamperedToken = tamperSignature(accessToken);

        HttpResponse<String> response = authorizedGet("/api/v1/auth/me", tamperedToken);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE)).contains("Bearer");
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void refreshTokenCannotReferenceAUserFromAnotherCompany() {
        Instant now = Instant.parse("2026-07-22T00:00:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO refresh_token (
                    refresh_token_id, user_id, company_id, token_family_id, token_hash,
                    expires_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                VIEWER_A,
                COMPANY_B,
                UUID.randomUUID(),
                "a".repeat(64),
                now.plusSeconds(3600),
                now,
                now,
                0L
        )).isInstanceOf(DataAccessException.class);
        assertThat(refreshTokenCount()).isZero();
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

    private HttpResponse<String> login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
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

    private HttpResponse<String> authorizedPost(String path, String accessToken) throws Exception {
        return postJson(path, "{}", accessToken);
    }

    private HttpResponse<String> postJson(String path, String body, String accessToken) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (accessToken != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String companyProbePath(UUID companyId) {
        return "/api/v1/security-probe/companies/" + companyId;
    }

    private String cookieValue(String setCookie) {
        int valueStart = setCookie.indexOf('=') + 1;
        int valueEnd = setCookie.indexOf(';', valueStart);
        return setCookie.substring(valueStart, valueEnd);
    }

    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char firstSignatureCharacter = parts[2].charAt(0);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);
        return String.join(".", parts);
    }

    private int refreshTokenCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_token", Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        SecurityProbeController securityProbeController(
                ActorContextProvider actorContextProvider,
                ActorAuthorizer actorAuthorizer
        ) {
            return new SecurityProbeController(actorContextProvider, actorAuthorizer);
        }
    }

    @RestController
    @RequestMapping("/api/v1/security-probe")
    static class SecurityProbeController {

        private final ActorContextProvider actorContextProvider;
        private final ActorAuthorizer actorAuthorizer;

        SecurityProbeController(
                ActorContextProvider actorContextProvider,
                ActorAuthorizer actorAuthorizer
        ) {
            this.actorContextProvider = actorContextProvider;
            this.actorAuthorizer = actorAuthorizer;
        }

        @GetMapping("/companies/{companyId}")
        ResponseEntity<Void> readCompany(@PathVariable UUID companyId) {
            actorAuthorizer.requireSameCompany(
                    actorContextProvider.requireCurrentActor(),
                    companyId
            );
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/write")
        ResponseEntity<Void> write() {
            return ResponseEntity.noContent().build();
        }
    }
}
