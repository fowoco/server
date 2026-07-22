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
    private static final String REFRESH_TOKEN_COOKIE_NAME = "fowoco_refresh_token";

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
    void resetAuthenticationState() {
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update(
                "UPDATE user_account SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP, version = version + 1"
        );
        jdbcTemplate.update(
                "UPDATE company SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP, version = version + 1"
        );
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
    void refreshRotatesTokenAndPersistsTheReplacementChain() throws Exception {
        HttpResponse<String> loginResponse = login(VIEWER_A_EMAIL, PASSWORD);
        String oldRawRefreshToken = refreshToken(loginResponse);
        String oldTokenHash = refreshTokenHasher.hash(oldRawRefreshToken);
        UUID oldTokenId = tokenId(oldTokenHash);
        UUID tokenFamilyId = tokenFamilyId(oldTokenHash);

        HttpResponse<String> refreshResponse = refresh(oldRawRefreshToken);

        assertThat(refreshResponse.statusCode()).isEqualTo(200);
        assertThat(refreshResponse.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(refreshResponse.headers().firstValue(HttpHeaders.PRAGMA)).contains("no-cache");
        assertThat(JsonPath.<String>read(refreshResponse.body(), "$.token_type")).isEqualTo("Bearer");
        assertThat(JsonPath.<Number>read(refreshResponse.body(), "$.expires_in_seconds").longValue())
                .isPositive();

        String newRawRefreshToken = refreshToken(refreshResponse);
        String newTokenHash = refreshTokenHasher.hash(newRawRefreshToken);
        UUID replacementTokenId = tokenId(newTokenHash);
        String refreshedAccessToken = JsonPath.read(refreshResponse.body(), "$.access_token");

        assertThat(newRawRefreshToken).isNotEqualTo(oldRawRefreshToken);
        assertThat(refreshResponse.body())
                .doesNotContain(oldRawRefreshToken)
                .doesNotContain(newRawRefreshToken);
        assertThat(tokenFamilyId(newTokenHash)).isEqualTo(tokenFamilyId);
        assertThat(replacedByTokenId(oldTokenHash)).isEqualTo(replacementTokenId);
        assertThat(oldTokenStateCount(oldTokenId)).isEqualTo(1);
        assertThat(activeTokenCount(tokenFamilyId)).isEqualTo(1);
        assertThat(familyTokenCount(tokenFamilyId)).isEqualTo(2);

        HttpResponse<String> meResponse = authorizedGet("/api/v1/auth/me", refreshedAccessToken);
        assertThat(meResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(meResponse.body(), "$.user_id")).isEqualTo(VIEWER_A.toString());
        assertThat(JsonPath.<String>read(meResponse.body(), "$.company_id")).isEqualTo(COMPANY_A.toString());
    }

    @Test
    void replayingAUsedRefreshTokenRevokesTheWholeFamily() throws Exception {
        String oldRawRefreshToken = refreshToken(login(VIEWER_A_EMAIL, PASSWORD));
        UUID tokenFamilyId = tokenFamilyId(refreshTokenHasher.hash(oldRawRefreshToken));
        HttpResponse<String> firstRefresh = refresh(oldRawRefreshToken);
        String newRawRefreshToken = refreshToken(firstRefresh);

        HttpResponse<String> replayResponse = refresh(oldRawRefreshToken);

        assertInvalidRefreshResponse(replayResponse);
        assertCookieCleared(replayResponse);
        assertThat(familyTokenCount(tokenFamilyId)).isEqualTo(2);
        assertThat(revokedTokenCount(tokenFamilyId)).isEqualTo(2);
        assertThat(activeTokenCount(tokenFamilyId)).isZero();

        HttpResponse<String> replacementResponse = refresh(newRawRefreshToken);
        assertInvalidRefreshResponse(replacementResponse);
        assertCookieCleared(replacementResponse);
    }

    @Test
    void missingMalformedAndUnknownRefreshTokensUseTheSameErrorAndClearTheCookie() throws Exception {
        HttpResponse<String> missingTokenResponse = refresh(null);
        HttpResponse<String> malformedTokenResponse = refresh("not-a-refresh-token");
        HttpResponse<String> unknownTokenResponse = refresh("x".repeat(43));

        assertInvalidRefreshResponse(missingTokenResponse);
        assertInvalidRefreshResponse(malformedTokenResponse);
        assertInvalidRefreshResponse(unknownTokenResponse);
        assertThat(JsonPath.<String>read(missingTokenResponse.body(), "$.message"))
                .isEqualTo(JsonPath.<String>read(malformedTokenResponse.body(), "$.message"))
                .isEqualTo(JsonPath.<String>read(unknownTokenResponse.body(), "$.message"));
        assertCookieCleared(missingTokenResponse);
        assertCookieCleared(malformedTokenResponse);
        assertCookieCleared(unknownTokenResponse);
        assertThat(refreshTokenCount()).isZero();
    }

    @Test
    void logoutIsIdempotentAndAlwaysClearsTheCookie() throws Exception {
        HttpResponse<String> loginResponse = login(VIEWER_A_EMAIL, PASSWORD);
        String accessToken = accessToken(loginResponse);
        String rawRefreshToken = refreshToken(loginResponse);
        UUID tokenFamilyId = tokenFamilyId(refreshTokenHasher.hash(rawRefreshToken));

        HttpResponse<String> firstLogout = logout(rawRefreshToken);
        HttpResponse<String> secondLogout = logout(rawRefreshToken);
        HttpResponse<String> missingTokenLogout = logout(null);

        assertLogoutResponse(firstLogout);
        assertLogoutResponse(secondLogout);
        assertLogoutResponse(missingTokenLogout);
        assertThat(familyTokenCount(tokenFamilyId)).isEqualTo(1);
        assertThat(revokedTokenCount(tokenFamilyId)).isEqualTo(1);
        assertThat(activeTokenCount(tokenFamilyId)).isZero();
        assertInvalidRefreshResponse(refresh(rawRefreshToken));
        assertThat(authorizedGet("/api/v1/auth/me", accessToken).statusCode()).isEqualTo(200);
    }

    @Test
    void logoutRevokesOnlyThePresentedLoginFamily() throws Exception {
        String firstFamilyToken = refreshToken(login(VIEWER_A_EMAIL, PASSWORD));
        String secondFamilyToken = refreshToken(login(VIEWER_A_EMAIL, PASSWORD));
        UUID firstFamilyId = tokenFamilyId(refreshTokenHasher.hash(firstFamilyToken));
        UUID secondFamilyId = tokenFamilyId(refreshTokenHasher.hash(secondFamilyToken));

        HttpResponse<String> logoutResponse = logout(firstFamilyToken);

        assertLogoutResponse(logoutResponse);
        assertThat(firstFamilyId).isNotEqualTo(secondFamilyId);
        assertThat(revokedTokenCount(firstFamilyId)).isEqualTo(1);
        assertThat(activeTokenCount(firstFamilyId)).isZero();
        assertThat(revokedTokenCount(secondFamilyId)).isZero();
        assertThat(activeTokenCount(secondFamilyId)).isEqualTo(1);
        assertThat(refresh(secondFamilyToken).statusCode()).isEqualTo(200);
    }

    @Test
    void refreshRejectsInactiveUserAndCompanyAndRevokesTheirFamilies() throws Exception {
        String inactiveUserToken = refreshToken(login(VIEWER_A_EMAIL, PASSWORD));
        UUID inactiveUserFamilyId = tokenFamilyId(refreshTokenHasher.hash(inactiveUserToken));
        jdbcTemplate.update(
                "UPDATE user_account SET status = 'SUSPENDED', updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE user_id = ?",
                VIEWER_A
        );

        HttpResponse<String> inactiveUserResponse = refresh(inactiveUserToken);

        assertInvalidRefreshResponse(inactiveUserResponse);
        assertCookieCleared(inactiveUserResponse);
        assertThat(revokedTokenCount(inactiveUserFamilyId)).isEqualTo(1);

        jdbcTemplate.update(
                "UPDATE user_account SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE user_id = ?",
                VIEWER_A
        );
        String inactiveCompanyToken = refreshToken(login(VIEWER_A_EMAIL, PASSWORD));
        UUID inactiveCompanyFamilyId = tokenFamilyId(refreshTokenHasher.hash(inactiveCompanyToken));
        jdbcTemplate.update(
                "UPDATE company SET status = 'SUSPENDED', updated_at = CURRENT_TIMESTAMP, "
                        + "version = version + 1 WHERE company_id = ?",
                COMPANY_A
        );

        HttpResponse<String> inactiveCompanyResponse = refresh(inactiveCompanyToken);

        assertInvalidRefreshResponse(inactiveCompanyResponse);
        assertCookieCleared(inactiveCompanyResponse);
        assertThat(revokedTokenCount(inactiveCompanyFamilyId)).isEqualTo(1);
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

    private HttpResponse<String> refresh(String rawRefreshToken) throws Exception {
        return postWithRefreshTokenCookie("/api/v1/auth/refresh", rawRefreshToken);
    }

    private HttpResponse<String> logout(String rawRefreshToken) throws Exception {
        return postWithRefreshTokenCookie("/api/v1/auth/logout", rawRefreshToken);
    }

    private HttpResponse<String> postWithRefreshTokenCookie(String path, String rawRefreshToken)
            throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri(path));
        if (rawRefreshToken != null) {
            requestBuilder.header(
                    HttpHeaders.COOKIE,
                    REFRESH_TOKEN_COOKIE_NAME + "=" + rawRefreshToken
            );
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String refreshToken(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        String setCookie = response.headers().firstValue(HttpHeaders.SET_COOKIE).orElseThrow();
        assertThat(setCookie)
                .contains(REFRESH_TOKEN_COOKIE_NAME + "=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        return cookieValue(setCookie);
    }

    private void assertInvalidRefreshResponse(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    private void assertLogoutResponse(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(response.headers().firstValue(HttpHeaders.PRAGMA)).contains("no-cache");
        assertCookieCleared(response);
    }

    private void assertCookieCleared(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue(HttpHeaders.SET_COOKIE).orElseThrow();
        assertThat(setCookie)
                .contains(REFRESH_TOKEN_COOKIE_NAME + "=;")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
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

    private UUID tokenId(String tokenHash) {
        return jdbcTemplate.queryForObject(
                "SELECT refresh_token_id FROM refresh_token WHERE token_hash = ?",
                UUID.class,
                tokenHash
        );
    }

    private UUID tokenFamilyId(String tokenHash) {
        return jdbcTemplate.queryForObject(
                "SELECT token_family_id FROM refresh_token WHERE token_hash = ?",
                UUID.class,
                tokenHash
        );
    }

    private UUID replacedByTokenId(String tokenHash) {
        return jdbcTemplate.queryForObject(
                "SELECT replaced_by_token_id FROM refresh_token WHERE token_hash = ?",
                UUID.class,
                tokenHash
        );
    }

    private int oldTokenStateCount(UUID refreshTokenId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token
                WHERE refresh_token_id = ?
                  AND used_at IS NOT NULL
                  AND revoked_at IS NULL
                  AND replaced_by_token_id IS NOT NULL
                """,
                Integer.class,
                refreshTokenId
        );
    }

    private int familyTokenCount(UUID tokenFamilyId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_family_id = ?",
                Integer.class,
                tokenFamilyId
        );
    }

    private int activeTokenCount(UUID tokenFamilyId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token
                WHERE token_family_id = ?
                  AND used_at IS NULL
                  AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """,
                Integer.class,
                tokenFamilyId
        );
    }

    private int revokedTokenCount(UUID tokenFamilyId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token
                WHERE token_family_id = ?
                  AND revoked_at IS NOT NULL
                """,
                Integer.class,
                tokenFamilyId
        );
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
