package com.fowoco.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.fowoco.server.auth.application.port.PasswordResetNotificationPort;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fowoco-password-reset-test;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetIntegrationTest {

    private static final String EMAIL = "owner@example.com";
    private static final String OLD_PASSWORD = "Old-password-1!";
    private static final String NEW_PASSWORD = "New-password-2!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PasswordResetNotificationPort notificationPort;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        reset(notificationPort);
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM password_reset_token");
        jdbcTemplate.update("DELETE FROM user_agreement_consent");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
        assertThat(signup().statusCode()).isEqualTo(201);
        reset(notificationPort);
    }

    @Test
    void requestAndCompleteResetChangesPasswordRevokesSessionsAndConsumesToken() throws Exception {
        HttpResponse<String> oldLogin = login(OLD_PASSWORD);
        assertThat(oldLogin.statusCode()).isEqualTo(200);
        String oldRefreshCookie = oldLogin.headers().firstValue(HttpHeaders.SET_COOKIE).orElseThrow();

        HttpResponse<String> request = postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        );
        assertThat(request.statusCode()).isEqualTo(202);
        assertThat(request.body()).isEmpty();

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(notificationPort, timeout(1_000)).sendResetLink(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                tokenCaptor.capture(),
                expiryCaptor.capture()
        );
        String rawToken = tokenCaptor.getValue();
        assertThat(rawToken).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(expiryCaptor.getValue()).isAfter(Instant.now());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT token_hash FROM password_reset_token",
                String.class
        )).hasSize(64).isNotEqualTo(rawToken);

        HttpResponse<String> complete = postJson(
                "/api/v1/auth/password-resets",
                """
                {"token":"%s","new_password":"%s"}
                """.formatted(rawToken, NEW_PASSWORD)
        );
        assertThat(complete.statusCode()).isEqualTo(204);
        assertThat(login(OLD_PASSWORD).statusCode()).isEqualTo(401);
        assertThat(login(NEW_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(refresh(oldRefreshCookie).statusCode()).isEqualTo(401);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_token WHERE used_at IS NOT NULL",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'PASSWORD_RESET_COMPLETED'",
                Integer.class
        )).isEqualTo(1);

        HttpResponse<String> replay = postJson(
                "/api/v1/auth/password-resets",
                """
                {"token":"%s","new_password":"%s"}
                """.formatted(rawToken, "Another-password-3!")
        );
        assertThat(replay.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(replay.body(), "$.code"))
                .isEqualTo("INVALID_PASSWORD_RESET_TOKEN");
    }

    @Test
    void completingOneResetInvalidatesEveryOtherUnusedLinkForTheAccount() throws Exception {
        assertThat(postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        ).statusCode()).isEqualTo(202);
        ArgumentCaptor<String> firstTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationPort, timeout(1_000)).sendResetLink(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                firstTokenCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        String firstToken = firstTokenCaptor.getValue();

        jdbcTemplate.update(
                """
                UPDATE password_reset_token
                SET created_at = DATEADD('MINUTE', -2, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                """
        );
        reset(notificationPort);

        assertThat(postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        ).statusCode()).isEqualTo(202);
        ArgumentCaptor<String> secondTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationPort, timeout(1_000)).sendResetLink(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                secondTokenCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );

        assertThat(completeReset(secondTokenCaptor.getValue(), NEW_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_token WHERE used_at IS NOT NULL",
                Integer.class
        )).isEqualTo(2);

        HttpResponse<String> oldLinkAttempt = completeReset(firstToken, "Another-password-3!");
        assertThat(oldLinkAttempt.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(oldLinkAttempt.body(), "$.code"))
                .isEqualTo("INVALID_PASSWORD_RESET_TOKEN");
    }

    @Test
    void notificationProviderFailureDoesNotChangeTheAcceptedResponse() throws Exception {
        doThrow(new IllegalStateException("simulated provider failure"))
                .when(notificationPort)
                .sendResetLink(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(Instant.class)
                );

        HttpResponse<String> response = postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        );

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
        verify(notificationPort, timeout(1_000)).sendResetLink(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_token",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void unknownEmailReturnsTheSameAcceptedResponseWithoutIssuingAToken() throws Exception {
        HttpResponse<String> response = postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"unknown@example.com\"}"
        );

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
        verify(notificationPort, never()).sendResetLink(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_token",
                Integer.class
        )).isZero();
    }

    @Test
    void repeatedRequestDuringCooldownReturnsAcceptedButIssuesOnlyOneToken() throws Exception {
        assertThat(postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        ).statusCode()).isEqualTo(202);
        assertThat(postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        ).statusCode()).isEqualTo(202);

        verify(notificationPort, timeout(1_000)).sendResetLink(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_token",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void concurrentRequestsIssueOnlyOneActiveToken() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> requestResetAfter(start));
            Future<Integer> second = executor.submit(() -> requestResetAfter(start));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactly(202, 202);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        verify(notificationPort, timeout(1_000)).sendResetLink(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_token",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void expiredAndForgedTokensAreRejected() throws Exception {
        assertThat(postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        ).statusCode()).isEqualTo(202);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationPort, timeout(1_000)).sendResetLink(
                org.mockito.ArgumentMatchers.eq(EMAIL),
                tokenCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );

        jdbcTemplate.update(
                """
                UPDATE password_reset_token
                SET created_at = DATEADD('HOUR', -2, CURRENT_TIMESTAMP),
                    expires_at = DATEADD('HOUR', -1, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP
                """
        );
        assertThat(completeReset(tokenCaptor.getValue(), NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(completeReset("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NEW_PASSWORD).statusCode())
                .isEqualTo(400);
    }

    private HttpResponse<String> signup() throws Exception {
        return postJson("/api/v1/auth/signup", """
                {
                  "company_name":"한빛정밀",
                  "display_name":"관리자",
                  "email":"owner@example.com",
                  "password":"Old-password-1!",
                  "agreements":{
                    "service_terms":{"agreed":true,"version":"1.0"},
                    "privacy_policy":{"agreed":true,"version":"1.0"},
                    "marketing":{"agreed":false,"version":"1.0"}
                  }
                }
                """);
    }

    private int requestResetAfter(CountDownLatch start) throws Exception {
        start.await();
        return postJson(
                "/api/v1/auth/password-reset-requests",
                "{\"email\":\"owner@example.com\"}"
        ).statusCode();
    }

    private HttpResponse<String> completeReset(String token, String password) throws Exception {
        return postJson(
                "/api/v1/auth/password-resets",
                """
                {"token":"%s","new_password":"%s"}
                """.formatted(token, password)
        );
    }

    private HttpResponse<String> login(String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {"email":"owner@example.com","password":"%s"}
                """.formatted(password));
    }

    private HttpResponse<String> refresh(String cookie) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/refresh"))
                .header(HttpHeaders.COOKIE, cookie.split(";", 2)[0])
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
