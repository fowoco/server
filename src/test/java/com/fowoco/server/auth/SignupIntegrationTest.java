package com.fowoco.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fowoco-signup-test;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SignupIntegrationTest {

    private static final String PASSWORD = "Signup-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanSignupData() {
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void signupCreatesOneCompanyAndInitialAdminThenExistingLoginWorks() throws Exception {
        HttpResponse<String> signupResponse = signup(
                "한빛정밀",
                "김경민",
                " Admin@Example.com ",
                PASSWORD
        );

        assertThat(signupResponse.statusCode()).isEqualTo(201);
        assertThat(signupResponse.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(signupResponse.headers().firstValue(HttpHeaders.PRAGMA)).contains("no-cache");
        assertThat(signupResponse.headers().firstValue(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(JsonPath.<String>read(signupResponse.body(), "$.company_name")).isEqualTo("한빛정밀");
        assertThat(JsonPath.<String>read(signupResponse.body(), "$.display_name")).isEqualTo("김경민");
        assertThat(JsonPath.<String>read(signupResponse.body(), "$.email")).isEqualTo("Admin@Example.com");
        assertThat(JsonPath.<String>read(signupResponse.body(), "$.role")).isEqualTo("ADMIN");
        assertThat(signupResponse.body())
                .doesNotContain(PASSWORD)
                .doesNotContain("password_hash", "access_token", "refresh_token");

        String userId = JsonPath.read(signupResponse.body(), "$.user_id");
        String companyId = JsonPath.read(signupResponse.body(), "$.company_id");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE company_id = ? AND name = ? AND status = 'ACTIVE'",
                Integer.class,
                companyId,
                "한빛정밀"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM user_account
                WHERE user_id = ? AND company_id = ? AND display_name = ?
                  AND normalized_email = ? AND role = 'ADMIN' AND status = 'ACTIVE'
                """,
                Integer.class,
                userId,
                companyId,
                "김경민",
                "admin@example.com"
        )).isEqualTo(1);

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE user_id = ?",
                String.class,
                userId
        );
        assertThat(passwordHash).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();

        HttpResponse<String> loginResponse = login("admin@example.com", PASSWORD);
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.user_id")).isEqualTo(userId);
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.company_id")).isEqualTo(companyId);
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.company_name")).isEqualTo("한빛정밀");
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.display_name")).isEqualTo("김경민");
        assertThat(JsonPath.<String>read(loginResponse.body(), "$.role")).isEqualTo("ADMIN");
    }

    @Test
    void duplicateNormalizedEmailReturnsConflictAndRollsBackNewCompany() throws Exception {
        assertThat(signup("첫 번째 사업장", "첫 관리자", "owner@example.com", PASSWORD).statusCode())
                .isEqualTo(201);

        HttpResponse<String> duplicate = signup(
                "남으면 안 되는 사업장",
                "두 번째 관리자",
                " OWNER@EXAMPLE.COM ",
                PASSWORD
        );

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(duplicate.body(), "$.code"))
                .isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM company", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE name = '남으면 안 되는 사업장'",
                Integer.class
        )).isZero();
    }

    @Test
    void concurrentSignupWithTheSameEmailLeavesExactlyOneTenant() {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<HttpResponse<String>> first = concurrentSignup(
                    executor,
                    start,
                    "동시 사업장 A"
            );
            CompletableFuture<HttpResponse<String>> second = concurrentSignup(
                    executor,
                    start,
                    "동시 사업장 B"
            );

            start.countDown();
            List<Integer> statuses = List.of(first.join().statusCode(), second.join().statusCode());

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM company", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidOrClientControlledFieldsAreRejectedWithoutPartialData() throws Exception {
        assertBadRequest("""
                {
                  "company_name": "",
                  "display_name": "담당자",
                  "email": "owner@example.com",
                  "password": "Signup-password-1!"
                }
                """);
        assertBadRequest("""
                {
                  "company_name": "사업장",
                  "display_name": "담당자",
                  "email": "not-an-email",
                  "password": "Signup-password-1!"
                }
                """);
        assertBadRequest("""
                {
                  "company_name": "사업장",
                  "display_name": "담당자",
                  "email": "owner@example.com",
                  "password": "short"
                }
                """);
        assertBadRequest("""
                {
                  "company_name": "사업장",
                  "display_name": "담당자",
                  "email": "owner@example.com",
                  "password": "Signup-password-1!",
                  "role": "ADMIN",
                  "company_id": "10000000-0000-0000-0000-000000000001"
                }
                """);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM company", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_account", Integer.class)).isZero();
    }

    private void assertBadRequest(String body) throws Exception {
        HttpResponse<String> response = postJson("/api/v1/auth/signup", body);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isIn("VALIDATION_FAILED", "INVALID_REQUEST");
    }

    private HttpResponse<String> signup(
            String companyName,
            String displayName,
            String email,
            String password
    ) throws Exception {
        return postJson("/api/v1/auth/signup", """
                {
                  "company_name": "%s",
                  "display_name": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(companyName, displayName, email, password));
    }

    private CompletableFuture<HttpResponse<String>> concurrentSignup(
            ExecutorService executor,
            CountDownLatch start,
            String companyName
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                return signup(companyName, "동시 관리자", "concurrent@example.com", PASSWORD);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password));
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
