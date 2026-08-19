package com.fowoco.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fowoco-account-pii-test;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "app.auth.pii.enabled=true",
                "app.auth.pii.current-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "app.auth.pii.current-key-version=test-v1"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountPiiEncryptionIntegrationTest {

    private static final String PASSWORD = "Signup-password-1!";
    private static final String PHONE = "010-1234-5678";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanData() {
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM password_reset_token");
        jdbcTemplate.update("DELETE FROM user_agreement_consent");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company_settings");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void profileUpdateStoresPhoneOnlyAsCiphertextAndReturnsDecryptedValue() throws Exception {
        HttpResponse<String> signup = postJson("/api/v1/auth/signup", signupBody());
        assertThat(signup.statusCode()).isEqualTo(201);
        HttpResponse<String> login = postJson("/api/v1/auth/login", """
                {"email":"owner@example.com","password":"Signup-password-1!"}
                """);
        String accessToken = JsonPath.read(login.body(), "$.access_token");
        String userId = JsonPath.read(login.body(), "$.user_id");

        HttpResponse<String> updated = patchJson(
                "/api/v1/auth/me/profile",
                """
                {"display_name":"담당자","phone":"010-1234-5678"}
                """,
                accessToken
        );

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(updated.body(), "$.phone")).isEqualTo(PHONE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone FROM user_account WHERE user_id = ?",
                String.class,
                userId
        )).isNull();
        String ciphertext = jdbcTemplate.queryForObject(
                "SELECT phone_ciphertext FROM user_account WHERE user_id = ?",
                String.class,
                userId
        );
        assertThat(ciphertext).startsWith("v1.").doesNotContain(PHONE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone_key_version FROM user_account WHERE user_id = ?",
                String.class,
                userId
        )).isEqualTo("test-v1");

        HttpResponse<String> profile = get("/api/v1/auth/me/profile", accessToken);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(profile.body(), "$.phone")).isEqualTo(PHONE);
    }

    @Test
    void legacyPlaintextPhoneIsMigratedOnAuthenticatedAccountAccess() throws Exception {
        HttpResponse<String> signup = postJson("/api/v1/auth/signup", signupBody());
        String userId = JsonPath.read(signup.body(), "$.user_id");
        jdbcTemplate.update(
                "UPDATE user_account SET phone = ?, phone_ciphertext = NULL, phone_key_version = NULL WHERE user_id = ?",
                PHONE,
                userId
        );

        HttpResponse<String> login = postJson("/api/v1/auth/login", """
                {"email":"owner@example.com","password":"Signup-password-1!"}
                """);

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE user_id = ? AND phone IS NULL "
                        + "AND phone_ciphertext IS NOT NULL AND phone_key_version = 'test-v1'",
                Integer.class,
                userId
        )).isEqualTo(1);
    }

    private String signupBody() {
        return """
                {
                  "company_name":"한빛정밀",
                  "display_name":"담당자",
                  "email":"owner@example.com",
                  "password":"Signup-password-1!",
                  "agreements":{
                    "service_terms":{"agreed":true,"version":"1.0"},
                    "privacy_policy":{"agreed":true,"version":"1.0"},
                    "marketing":{"agreed":false,"version":"1.0"}
                  }
                }
                """;
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> patchJson(String path, String body, String accessToken) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> get(String path, String accessToken) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
