package com.fowoco.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.domain.RefreshToken;
import com.fowoco.server.auth.infrastructure.persistence.JpaRefreshTokenRepository;
import com.fowoco.server.auth.infrastructure.security.RefreshTokenHasher;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthRefreshPostgreSqlConcurrencyTest.ConcurrencyConfiguration.class)
class AuthRefreshPostgreSqlConcurrencyTest {

    private static final UUID COMPANY_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final String EMAIL = "postgres.refresh@example.com";
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

    @Autowired
    private CoordinatedRefreshTokenRepository coordinatedRefreshTokenRepository;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void usePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironmentVariable("POSTGRES_TEST_URL"));
        registry.add(
                "spring.datasource.username",
                () -> requiredEnvironmentVariable("POSTGRES_TEST_USERNAME")
        );
        registry.add(
                "spring.datasource.password",
                () -> requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD")
        );
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-postgresql"
        );
    }

    @BeforeEach
    void seedActiveAccount() {
        clearAuthenticationRows();
        jdbcTemplate.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, 'PostgreSQL 동시성 테스트 사업장', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                USER_ID,
                COMPANY_ID,
                EMAIL,
                EMAIL,
                passwordEncoder.encode(PASSWORD)
        );
    }

    @AfterEach
    void cleanUpAuthenticationRows() {
        clearAuthenticationRows();
    }

    @Test
    void concurrentRefreshWithTheSameTokenAllowsOneRotationAndRevokesTheFamilyOnReplay()
            throws Exception {
        HttpResponse<String> loginResponse = login();
        assertThat(loginResponse.statusCode()).isEqualTo(200);

        String originalRawToken = refreshTokenFrom(loginResponse);
        String originalTokenHash = refreshTokenHasher.hash(originalRawToken);
        UUID originalTokenId = tokenId(originalTokenHash);
        UUID tokenFamilyId = tokenFamilyId(originalTokenHash);

        coordinatedRefreshTokenRepository.coordinateNextTwoLookups();
        List<HttpResponse<String>> concurrentResponses = concurrentlyRefresh(originalRawToken);

        assertThat(concurrentResponses)
                .extracting(HttpResponse::statusCode)
                .containsExactlyInAnyOrder(200, 401);

        HttpResponse<String> successfulResponse = concurrentResponses.stream()
                .filter(response -> response.statusCode() == 200)
                .findFirst()
                .orElseThrow();
        HttpResponse<String> replayResponse = concurrentResponses.stream()
                .filter(response -> response.statusCode() == 401)
                .findFirst()
                .orElseThrow();
        assertThat(JsonPath.<String>read(replayResponse.body(), "$.code"))
                .isEqualTo("INVALID_REFRESH_TOKEN");

        String replacementRawToken = refreshTokenFrom(successfulResponse);
        assertThat(replacementRawToken).isNotEqualTo(originalRawToken);
        assertThat(familyTokenCount(tokenFamilyId)).isEqualTo(2);
        assertThat(childTokenCount(tokenFamilyId, originalTokenId)).isEqualTo(1);
        assertThat(activeChildTokenCount(tokenFamilyId, originalTokenId)).isZero();
        assertThat(activeTokenCount(tokenFamilyId)).isZero();
        assertThat(revokedTokenCount(tokenFamilyId)).isEqualTo(2);

        HttpResponse<String> replacementReplayResponse = refresh(replacementRawToken);

        assertThat(replacementReplayResponse.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(replacementReplayResponse.body(), "$.code"))
                .isEqualTo("INVALID_REFRESH_TOKEN");
        assertThat(familyTokenCount(tokenFamilyId)).isEqualTo(2);
        assertThat(activeTokenCount(tokenFamilyId)).isZero();
    }

    private List<HttpResponse<String>> concurrentlyRefresh(String rawRefreshToken) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpResponse<String>> first = executor.submit(
                    () -> refreshAfterSignal(rawRefreshToken, workersReady, start)
            );
            Future<HttpResponse<String>> second = executor.submit(
                    () -> refreshAfterSignal(rawRefreshToken, workersReady, start)
            );

            assertThat(workersReady.await(5, TimeUnit.SECONDS))
                    .as("both refresh requests must be ready before they are released")
                    .isTrue();
            start.countDown();
            return List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private HttpResponse<String> refreshAfterSignal(
            String rawRefreshToken,
            CountDownLatch workersReady,
            CountDownLatch start
    ) throws Exception {
        workersReady.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("refresh concurrency start signal timed out");
        }
        return refresh(rawRefreshToken);
    }

    private HttpResponse<String> login() throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> refresh(String rawRefreshToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/refresh"))
                .header(HttpHeaders.COOKIE, REFRESH_TOKEN_COOKIE_NAME + "=" + rawRefreshToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String refreshTokenFrom(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue(HttpHeaders.SET_COOKIE).orElseThrow();
        int valueStart = setCookie.indexOf('=') + 1;
        int valueEnd = setCookie.indexOf(';', valueStart);
        return setCookie.substring(valueStart, valueEnd);
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

    private int familyTokenCount(UUID tokenFamilyId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE token_family_id = ?",
                Integer.class,
                tokenFamilyId
        );
    }

    private int childTokenCount(UUID tokenFamilyId, UUID originalTokenId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token
                WHERE token_family_id = ?
                  AND refresh_token_id <> ?
                """,
                Integer.class,
                tokenFamilyId,
                originalTokenId
        );
    }

    private int activeChildTokenCount(UUID tokenFamilyId, UUID originalTokenId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token
                WHERE token_family_id = ?
                  AND refresh_token_id <> ?
                  AND used_at IS NULL
                  AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """,
                Integer.class,
                tokenFamilyId,
                originalTokenId
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

    private void clearAuthenticationRows() {
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyConfiguration {

        @Bean
        @Primary
        CoordinatedRefreshTokenRepository coordinatedRefreshTokenRepository(
                JpaRefreshTokenRepository delegate
        ) {
            return new CoordinatedRefreshTokenRepository(delegate);
        }
    }

    static final class CoordinatedRefreshTokenRepository implements RefreshTokenRepository {

        private final RefreshTokenRepository delegate;
        private final AtomicInteger coordinatedLookupCount = new AtomicInteger();
        private volatile CyclicBarrier lookupBarrier;

        CoordinatedRefreshTokenRepository(RefreshTokenRepository delegate) {
            this.delegate = delegate;
        }

        void coordinateNextTwoLookups() {
            coordinatedLookupCount.set(0);
            lookupBarrier = new CyclicBarrier(2);
        }

        @Override
        public void insert(RefreshToken refreshToken) {
            delegate.insert(refreshToken);
        }

        @Override
        public Optional<RefreshToken> findByTokenHashWithFamilyLock(String tokenHash) {
            CyclicBarrier currentBarrier = lookupBarrier;
            int lookupNumber = coordinatedLookupCount.incrementAndGet();
            if (currentBarrier != null && lookupNumber <= 2) {
                awaitConcurrentLookup(currentBarrier);
            }
            return delegate.findByTokenHashWithFamilyLock(tokenHash);
        }

        @Override
        public void update(RefreshToken refreshToken) {
            delegate.update(refreshToken);
        }

        @Override
        public int revokeFamily(UUID tokenFamilyId, Instant revokedAt) {
            return delegate.revokeFamily(tokenFamilyId, revokedAt);
        }

        private void awaitConcurrentLookup(CyclicBarrier barrier) {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("refresh lookup coordination was interrupted", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new IllegalStateException("two refresh lookups did not overlap", exception);
            }
        }
    }
}
