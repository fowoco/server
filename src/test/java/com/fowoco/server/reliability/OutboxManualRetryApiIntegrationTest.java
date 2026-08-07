package com.fowoco.server.reliability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.reliability.application.OutboxProcessor;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(OutboxManualRetryApiIntegrationTest.ManualRetryTestConfiguration.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.reliability.outbox.enabled=false"
)
class OutboxManualRetryApiIntegrationTest {

    private static final UUID COMPANY_A =
            UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("b1000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_A =
            UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID HR_A =
            UUID.fromString("a2000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_B =
            UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_A =
            UUID.fromString("a3000000-0000-0000-0000-000000000001");
    private static final String ADMIN_A_EMAIL = "outbox.admin.a@example.com";
    private static final String HR_A_EMAIL = "outbox.hr.a@example.com";
    private static final String ADMIN_B_EMAIL = "outbox.admin.b@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private ManualRetryProbeHandler probeHandler;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetAndSeed() {
        probeHandler.reset();
        jdbcTemplate.update("DELETE FROM outbox_manual_retry");
        jdbcTemplate.update("DELETE FROM ai_candidate_decision_task");
        jdbcTemplate.update("DELETE FROM ai_candidate_decision");
        jdbcTemplate.update("DELETE FROM ai_candidate_decision_batch");
        jdbcTemplate.update("DELETE FROM ai_candidate");
        jdbcTemplate.update("DELETE FROM ai_question");
        jdbcTemplate.update("DELETE FROM ai_attempt");
        jdbcTemplate.update("DELETE FROM ai_run");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM worker_response_upload");
        jdbcTemplate.update("DELETE FROM worker_document_upload_idempotency");
        jdbcTemplate.update("DELETE FROM worker_response");
        jdbcTemplate.update("DELETE FROM worker_link");
        jdbcTemplate.update("DELETE FROM document_request_draft_type");
        jdbcTemplate.update("DELETE FROM document_request_draft");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM workflow_case");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "Outbox 사업장 A");
        insertCompany(COMPANY_B, "Outbox 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(ADMIN_A, COMPANY_A, ADMIN_A_EMAIL, "ADMIN", passwordHash);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, "HR", passwordHash);
        insertUser(ADMIN_B, COMPANY_B, ADMIN_B_EMAIL, "ADMIN", passwordHash);
        insertReviewRequiredEvent(EVENT_A, COMPANY_A);
    }

    @Test
    void exhaustedEventGetsOneFreshHandlerAttemptAfterManualRetry() throws Exception {
        String token = login(ADMIN_A_EMAIL);

        HttpResponse<String> response = retry(
                EVENT_A,
                token,
                "outbox-retry-exhausted",
                validBody(0)
        );

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(attemptCount(EVENT_A)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT previous_attempt_count FROM outbox_manual_retry WHERE event_id = ?",
                Integer.class,
                EVENT_A
        )).isEqualTo(8);

        assertThat(outboxProcessor.processAvailable()).isEqualTo(1);
        assertThat(probeHandler.invocationCount()).isEqualTo(1);
        assertThat(status(EVENT_A)).isEqualTo("COMPLETED");
        assertThat(attemptCount(EVENT_A)).isEqualTo(1);
    }

    @Test
    void adminRetriesOnceWithoutExposingPayloadAndDuplicateRequestIsReplayed() throws Exception {
        String token = login(ADMIN_A_EMAIL);
        String body = """
                {"expected_version":0,"reason":"내부 handler 복구와 점검을 완료했습니다."}
                """;

        HttpResponse<String> first = retry(EVENT_A, token, "outbox-retry-001", body);

        assertThat(first.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<String>read(first.body(), "$.accepted_status")).isEqualTo("PENDING");
        assertThat(JsonPath.<Boolean>read(first.body(), "$.already_requested")).isFalse();
        assertThat(first.body())
                .doesNotContain("payload_json", "last_error_code", "secret-value");
        assertThat(status(EVENT_A)).isEqualTo("PENDING");
        assertThat(lastErrorCode(EVENT_A)).isNull();
        assertThat(count("outbox_manual_retry")).isEqualTo(1);
        assertThat(count("audit_event")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT action FROM audit_event WHERE target_id = ?",
                String.class,
                EVENT_A
        )).isEqualTo("OUTBOX_MANUAL_RETRY_REQUESTED");

        HttpResponse<String> duplicate = retry(EVENT_A, token, "outbox-retry-001", body);

        assertThat(duplicate.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<Boolean>read(duplicate.body(), "$.already_requested")).isTrue();
        assertThat(count("outbox_manual_retry")).isEqualTo(1);
        assertThat(count("audit_event")).isEqualTo(1);

        HttpResponse<String> conflictingReuse = retry(
                EVENT_A,
                token,
                "outbox-retry-001",
                """
                {"expected_version":0,"reason":"다른 원인으로 재처리를 다시 요청합니다."}
                """
        );
        assertThat(conflictingReuse.statusCode()).isEqualTo(409);
    }

    @Test
    void roleTenantStateAndVersionAreChecked() throws Exception {
        String adminA = login(ADMIN_A_EMAIL);

        assertThat(retry(
                EVENT_A,
                login(HR_A_EMAIL),
                "outbox-retry-hr",
                validBody(0)
        ).statusCode()).isEqualTo(403);
        assertThat(retry(
                EVENT_A,
                login(ADMIN_B_EMAIL),
                "outbox-retry-other-company",
                validBody(0)
        ).statusCode()).isEqualTo(404);
        assertThat(retry(
                EVENT_A,
                adminA,
                "outbox-retry-stale",
                validBody(1)
        ).statusCode()).isEqualTo(409);

        jdbcTemplate.update(
                "UPDATE event_publication SET status = 'COMPLETED', "
                        + "last_error_code = NULL, completed_at = CURRENT_TIMESTAMP WHERE event_id = ?",
                EVENT_A
        );
        assertThat(retry(
                EVENT_A,
                adminA,
                "outbox-retry-completed",
                validBody(0)
        ).statusCode()).isEqualTo(409);
    }

    @Test
    void expectedVersionIsRequired() throws Exception {
        String token = login(ADMIN_A_EMAIL);

        assertThat(retry(
                EVENT_A,
                token,
                "outbox-retry-missing-version",
                """
                {"reason":"내부 handler 복구와 점검을 완료했습니다."}
                """
        ).statusCode()).isEqualTo(400);
        assertThat(retry(
                EVENT_A,
                token,
                "outbox-retry-null-version",
                """
                {"expected_version":null,"reason":"내부 handler 복구와 점검을 완료했습니다."}
                """
        ).statusCode()).isEqualTo(400);
        assertThat(status(EVENT_A)).isEqualTo("REVIEW_REQUIRED");
        assertThat(count("outbox_manual_retry")).isZero();
    }

    @Test
    void idempotencyKeyHeaderIsRequiredWithoutChangingEvent() throws Exception {
        String token = login(ADMIN_A_EMAIL);

        HttpResponse<String> response = retryWithoutIdempotencyKey(
                EVENT_A,
                token,
                validBody(0)
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INVALID_REQUEST");
        assertThat(status(EVENT_A)).isEqualTo("REVIEW_REQUIRED");
        assertThat(attemptCount(EVENT_A)).isEqualTo(8);
        assertThat(count("outbox_manual_retry")).isZero();
        assertThat(count("audit_event")).isZero();
    }

    @Test
    void concurrentRequestsAllowOnlyOneRetry() throws Exception {
        String token = login(ADMIN_A_EMAIL);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpResponse<String>> first = executor.submit(
                    () -> retryAfterSignal("outbox-concurrent-1", token, ready, start)
            );
            Future<HttpResponse<String>> second = executor.submit(
                    () -> retryAfterSignal("outbox-concurrent-2", token, ready, start)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS).statusCode(),
                    second.get(10, TimeUnit.SECONDS).statusCode()
            )).containsExactlyInAnyOrder(202, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(count("outbox_manual_retry")).isEqualTo(1);
        assertThat(count("audit_event")).isEqualTo(1);
        assertThat(status(EVENT_A)).isEqualTo("PENDING");
    }

    @Test
    void openApiPublishesAdminRetryContract() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/admin/outbox-events/{eventId}/retry'].post.operationId"
        )).isEqualTo("retryOutboxEvent");
        assertThat(response.body())
                .contains("Idempotency-Key", "expected_version", "reason", "bearerAuth");
    }

    private HttpResponse<String> retryAfterSignal(
            String key,
            String token,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return retry(EVENT_A, token, key, validBody(0));
    }

    private HttpResponse<String> retry(UUID eventId, String token, String key, String body)
            throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/admin/outbox-events/" + eventId + "/retry"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .header("Idempotency-Key", key)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> retryWithoutIdempotencyKey(UUID eventId, String token, String body)
            throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/admin/outbox-events/" + eventId + "/retry"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String validBody(long version) {
        return """
                {"expected_version":%d,"reason":"내부 handler 복구와 점검을 완료했습니다."}
                """.formatted(version);
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status) VALUES (?, ?, 'ACTIVE')",
                companyId,
                name
        );
    }

    private void insertUser(
            UUID userId,
            UUID companyId,
            String email,
            String role,
            String passwordHash
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status, display_name
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', '운영 테스트')
                """,
                userId,
                companyId,
                email,
                email,
                passwordHash,
                role
        );
    }

    private void insertReviewRequiredEvent(UUID eventId, UUID companyId) {
        jdbcTemplate.update(
                """
                INSERT INTO event_publication (
                    event_id, company_id, event_type, payload_version,
                    aggregate_type, aggregate_id, actor_type, actor_id,
                    request_id, payload_json, status, attempt_count,
                    last_error_code, occurred_at, created_at, updated_at, version
                ) VALUES (
                    ?, ?, 'ReliabilityTestRequested', '1',
                    'ReliabilityProbe', ?, 'SYSTEM_RULE', NULL,
                    'outbox-manual-retry-fixture', '{"result":"secret-value"}',
                    'REVIEW_REQUIRED', 8, 'TEST_PAYLOAD_REJECTED',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                """,
                eventId,
                companyId,
                UUID.randomUUID()
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String status(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM event_publication WHERE event_id = ?",
                String.class,
                eventId
        );
    }

    private String lastErrorCode(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_error_code FROM event_publication WHERE event_id = ?",
                String.class,
                eventId
        );
    }

    private int attemptCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM event_publication WHERE event_id = ?",
                Integer.class,
                eventId
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ManualRetryTestConfiguration {

        @Bean
        ManualRetryProbeHandler manualRetryProbeHandler() {
            return new ManualRetryProbeHandler();
        }
    }

    static final class ManualRetryProbeHandler implements DomainEventHandler {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public String handlerName() {
            return "manual-retry-probe-v1";
        }

        @Override
        public boolean supports(String eventType) {
            return "ReliabilityTestRequested".equals(eventType);
        }

        @Override
        public void handle(DomainEventEnvelope event) {
            invocationCount.incrementAndGet();
        }

        int invocationCount() {
            return invocationCount.get();
        }

        void reset() {
            invocationCount.set(0);
        }
    }
}
