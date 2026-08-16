package com.fowoco.server.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.ocr.AiOcrStatus;
import com.fowoco.server.aiintegration.application.port.AiOcrClient;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.reliability.application.OutboxProcessor;
import com.fowoco.server.reliability.application.OutboxClaimService;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.document.ocr.enabled=true",
                "app.document.ocr.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "app.document.ocr.key-version=test-v1",
                "app.reliability.outbox.enabled=false"
        }
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentOcrApiIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("c2000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("c3000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("c4000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A = UUID.fromString("c5000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_B = UUID.fromString("c6000000-0000-0000-0000-000000000002");
    private static final UUID FILE_A = UUID.fromString("c7000000-0000-0000-0000-000000000001");
    private static final UUID FILE_B = UUID.fromString("c8000000-0000-0000-0000-000000000002");
    private static final UUID DOCUMENT_A = UUID.fromString("c9000000-0000-0000-0000-000000000001");
    private static final UUID DOCUMENT_B = UUID.fromString("ca000000-0000-0000-0000-000000000002");
    private static final String HR_A_EMAIL = "hr.ocr.a@example.com";
    private static final String HR_B_EMAIL = "hr.ocr.b@example.com";
    private static final String PASSWORD = "Test-password-1!";
    private static final byte[] FILE_CONTENT = "fake-passport-image".getBytes(StandardCharsets.UTF_8);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestFileStorage fileStorage;

    @Autowired
    private TestAiOcrClient aiOcrClient;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private OutboxClaimService outboxClaimService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @BeforeAll
    void seedAccounts() {
        deleteFixture();
        insertCompany(COMPANY_A, "OCR 사업장 A");
        insertCompany(COMPANY_B, "OCR 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash);
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash);
    }

    @BeforeEach
    void resetOcrFixture() {
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM document_ocr_run");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM worker");
        insertWorker(WORKER_A, COMPANY_A, "응웬반안", "VN");
        insertWorker(WORKER_B, COMPANY_B, "마리아", "PH");
        insertStoredFile(FILE_A, COMPANY_A, WORKER_A, "ocr-a");
        insertStoredFile(FILE_B, COMPANY_B, WORKER_B, "ocr-b");
        insertDocument(DOCUMENT_A, WORKER_A, COMPANY_A, FILE_A);
        insertDocument(DOCUMENT_B, WORKER_B, COMPANY_B, FILE_B);
        fileStorage.reset(Map.of("ocr-a", FILE_CONTENT, "ocr-b", FILE_CONTENT));
        aiOcrClient.reset();
    }

    @Test
    void ocrRunIsRecoveredAfterExpiredLeaseThenEncryptedAndReviewedWithoutUpdatingWorker() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> created = postOcr(DOCUMENT_A, "ocr-request-0001", token);

        assertThat(created.statusCode()).as(created.body()).isEqualTo(202);
        UUID runId = UUID.fromString(JsonPath.read(created.body(), "$.ocr_run_id"));
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("QUEUED");
        assertThat(aiOcrClient.requests()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM event_publication WHERE aggregate_id = ?",
                String.class,
                runId
        )).isEqualTo("PENDING");

        UUID eventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM event_publication WHERE aggregate_id = ?",
                UUID.class,
                runId
        );
        assertThat(outboxClaimService.claimBatch("stopped-ocr-server"))
                .containsExactly(new OutboxClaimService.ClaimedEvent(eventId, COMPANY_A));
        jdbcTemplate.update(
                """
                UPDATE document_ocr_run
                SET status = 'RUNNING', started_at = ?, updated_at = ?, version = version + 1
                WHERE ocr_run_id = ?
                """,
                java.time.Instant.now(),
                java.time.Instant.now(),
                runId
        );
        jdbcTemplate.update(
                "UPDATE event_publication SET lease_expires_at = ?, version = version + 1 WHERE event_id = ?",
                java.time.Instant.now().minusSeconds(60),
                eventId
        );

        assertThat(outboxProcessor.processAvailable()).isEqualTo(1);

        HttpResponse<String> completed = awaitStatus(DOCUMENT_A, runId, token, "READY_FOR_REVIEW");
        assertThat(JsonPath.<String>read(completed.body(), "$.result.fields.passport_number"))
                .isEqualTo("M12345678");
        assertThat(JsonPath.<String>read(completed.body(), "$.result.fields.surname"))
                .isEqualTo("NGUYEN");
        assertThat(aiOcrClient.requests()).hasSize(1);
        assertThat(aiOcrClient.requests().get(0).countryCode()).isEqualTo("VNM");

        String ciphertext = jdbcTemplate.queryForObject(
                "SELECT result_ciphertext FROM document_ocr_run WHERE ocr_run_id = ?",
                String.class,
                runId
        );
        assertThat(ciphertext).startsWith("v1.").doesNotContain("M12345678", "NGUYEN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'DOCUMENT_OCR_COMPLETED'",
                Integer.class,
                runId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'DOCUMENT_OCR_RESULT_VIEWED'",
                Integer.class,
                runId
        )).isGreaterThanOrEqualTo(1);

        long version = JsonPath.<Number>read(completed.body(), "$.version").longValue();
        HttpResponse<String> reviewed = review(
                DOCUMENT_A,
                runId,
                version,
                "APPROVE",
                null,
                Map.of("passport_number", "M87654321"),
                token
        );
        assertThat(reviewed.statusCode()).as(reviewed.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(reviewed.body(), "$.status")).isEqualTo("APPROVED");
        assertThat(JsonPath.<String>read(reviewed.body(), "$.result.fields.passport_number"))
                .isEqualTo("M12345678");
        assertThat(JsonPath.<String>read(reviewed.body(), "$.corrected_fields.passport_number"))
                .isEqualTo("M87654321");
        String correctedCiphertext = jdbcTemplate.queryForObject(
                "SELECT corrected_fields_ciphertext FROM document_ocr_run WHERE ocr_run_id = ?",
                String.class,
                runId
        );
        assertThat(correctedCiphertext).startsWith("v1.").doesNotContain("M87654321");
        String auditSummary = jdbcTemplate.queryForObject(
                "SELECT change_summary FROM audit_event WHERE target_id = ? AND action = 'DOCUMENT_OCR_APPROVED'",
                String.class,
                runId
        );
        assertThat(auditSummary).contains("passport_number").doesNotContain("M87654321");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT nationality_code FROM worker WHERE worker_id = ?",
                String.class,
                WORKER_A
        )).isEqualTo("VN");
    }

    @Test
    void sameIdempotencyKeyReturnsSameRunAndDoesNotCallAiTwice() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        HttpResponse<String> first = postOcr(DOCUMENT_A, "ocr-request-0002", token);
        UUID runId = UUID.fromString(JsonPath.read(first.body(), "$.ocr_run_id"));
        assertThat(outboxProcessor.processAvailable()).isEqualTo(1);
        awaitStatus(DOCUMENT_A, runId, token, "READY_FOR_REVIEW");

        HttpResponse<String> replay = postOcr(DOCUMENT_A, "ocr-request-0002", token);

        assertThat(replay.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<String>read(replay.body(), "$.ocr_run_id")).isEqualTo(runId.toString());
        assertThat(JsonPath.<Boolean>read(replay.body(), "$.already_requested")).isTrue();
        assertThat(aiOcrClient.requests()).hasSize(1);
    }

    @Test
    void otherCompanyCannotReadOcrRunAndMissingIdempotencyHeaderIsBadRequest() throws Exception {
        String tokenA = accessToken(login(HR_A_EMAIL));
        HttpResponse<String> created = postOcr(DOCUMENT_A, "ocr-request-0003", tokenA);
        UUID runId = UUID.fromString(JsonPath.read(created.body(), "$.ocr_run_id"));
        String tokenB = accessToken(login(HR_B_EMAIL));

        HttpResponse<String> otherCompany = get(
                "/api/v1/documents/" + DOCUMENT_A + "/ocr-runs/" + runId,
                tokenB
        );
        HttpResponse<String> missingHeader = post(
                "/api/v1/documents/" + DOCUMENT_B + "/ocr-runs",
                null,
                tokenB
        );

        assertThat(otherCompany.statusCode()).isEqualTo(404);
        assertThat(missingHeader.statusCode()).isEqualTo(400);
    }

    @Test
    void retryUsesNewRunAndDoesNotOverwriteFailedHistory() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        aiOcrClient.failNext();
        HttpResponse<String> first = postOcr(DOCUMENT_A, "ocr-request-0004", token);
        UUID failedRunId = UUID.fromString(JsonPath.read(first.body(), "$.ocr_run_id"));
        assertThat(outboxProcessor.processAvailable()).isEqualTo(1);
        HttpResponse<String> failed = awaitStatus(DOCUMENT_A, failedRunId, token, "FAILED");
        assertThat(JsonPath.<String>read(failed.body(), "$.error_code"))
                .isEqualTo("RUNTIME_UNAVAILABLE");

        HttpResponse<String> retry = postOcr(DOCUMENT_A, "ocr-request-0005", token);
        UUID retryRunId = UUID.fromString(JsonPath.read(retry.body(), "$.ocr_run_id"));
        assertThat(outboxProcessor.processAvailable()).isEqualTo(1);
        awaitStatus(DOCUMENT_A, retryRunId, token, "READY_FOR_REVIEW");

        assertThat(retryRunId).isNotEqualTo(failedRunId);
        assertThat(JsonPath.<String>read(
                get("/api/v1/documents/" + DOCUMENT_A + "/ocr-runs/" + failedRunId, token).body(),
                "$.status"
        )).isEqualTo("FAILED");
        assertThat(aiOcrClient.requests()).hasSize(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'DOCUMENT_OCR_FAILED'",
                Integer.class,
                failedRunId
        )).isEqualTo(1);
    }

    @Test
    void unknownCorrectionFieldIsRejectedWithoutChangingReviewState() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        HttpResponse<String> created = postOcr(DOCUMENT_A, "ocr-request-0006", token);
        UUID runId = UUID.fromString(JsonPath.read(created.body(), "$.ocr_run_id"));
        assertThat(outboxProcessor.processAvailable()).isEqualTo(1);
        HttpResponse<String> completed = awaitStatus(DOCUMENT_A, runId, token, "READY_FOR_REVIEW");
        long version = JsonPath.<Number>read(completed.body(), "$.version").longValue();

        HttpResponse<String> rejected = review(
                DOCUMENT_A,
                runId,
                version,
                "APPROVE",
                null,
                Map.of("raw_provider_response", "노출되면 안 되는 값"),
                token
        );

        assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(422);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM document_ocr_run WHERE ocr_run_id = ?",
                String.class,
                runId
        )).isEqualTo("READY_FOR_REVIEW");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT corrected_fields_ciphertext FROM document_ocr_run WHERE ocr_run_id = ?",
                String.class,
                runId
        )).isNull();
    }

    private HttpResponse<String> awaitStatus(
            UUID documentId,
            UUID runId,
            String token,
            String expectedStatus
    ) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        HttpResponse<String> response;
        do {
            response = get("/api/v1/documents/" + documentId + "/ocr-runs/" + runId, token);
            if (response.statusCode() == 200
                    && expectedStatus.equals(JsonPath.<String>read(response.body(), "$.status"))) {
                return response;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("OCR did not reach " + expectedStatus + ": " + response.body());
    }

    private HttpResponse<String> postOcr(UUID documentId, String key, String token) throws Exception {
        return post("/api/v1/documents/" + documentId + "/ocr-runs", key, token);
    }

    private HttpResponse<String> review(
            UUID documentId,
            UUID runId,
            long version,
            String decision,
            String reason,
            Map<String, String> correctedFields,
            String token
    ) throws Exception {
        String fieldsJson = correctedFields.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        String body = "{\"expected_version\":" + version
                + ",\"decision\":\"" + decision + "\""
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"")
                + ",\"corrected_fields\":" + fieldsJson + "}";
        HttpRequest request = HttpRequest.newBuilder(uri(
                        "/api/v1/documents/" + documentId + "/ocr-runs/" + runId + "/review"
                ))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String idempotencyKey, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

    private HttpResponse<String> login(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        return httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String accessToken(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void insertCompany(UUID id, String name) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status) VALUES (?, ?, 'ACTIVE')",
                id,
                name
        );
    }

    private void insertUser(UUID id, UUID companyId, String email, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash, role, status
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE')
                """,
                id,
                companyId,
                email,
                email,
                passwordHash
        );
    }

    private void insertWorker(UUID id, UUID companyId, String displayName, String nationality) {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality_code, work_status
                ) VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
                id,
                companyId,
                displayName,
                nationality
        );
    }

    private void insertStoredFile(UUID id, UUID companyId, UUID workerId, String storageKey) {
        jdbcTemplate.update(
                """
                INSERT INTO stored_file (
                    stored_file_id, company_id, name, mime_type, size, purpose,
                    worker_id, storage_key, scan_status
                ) VALUES (?, ?, 'passport.jpg', 'image/jpeg', ?, 'WORKER_DOCUMENT', ?, ?, 'NOT_SCANNED')
                """,
                id,
                companyId,
                FILE_CONTENT.length,
                workerId,
                storageKey
        );
    }

    private void insertDocument(UUID id, UUID workerId, UUID companyId, UUID fileId) {
        jdbcTemplate.update(
                """
                INSERT INTO worker_document (
                    worker_document_id, worker_id, company_id, document_type,
                    submission_status, file_id
                ) VALUES (?, ?, ?, 'PASSPORT_COPY', 'SUBMITTED', ?)
                """,
                id,
                workerId,
                companyId,
                fileId
        );
    }

    private void deleteFixture() {
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM document_ocr_run");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OcrTestConfiguration {

        @Bean
        @Primary
        TestFileStorage testFileStorage() {
            return new TestFileStorage();
        }

        @Bean
        @Primary
        TestAiOcrClient testAiOcrClient() {
            return new TestAiOcrClient();
        }
    }

    static final class TestFileStorage implements FileStorage {
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();

        void reset(Map<String, byte[]> next) {
            files.clear();
            next.forEach((key, value) -> files.put(key, value.clone()));
        }

        @Override
        public void store(String storageKey, InputStream content, long size, String mimeType) {
            throw new UnsupportedOperationException("not needed in OCR API test");
        }

        @Override
        public Optional<InputStream> open(String storageKey) {
            byte[] value = files.get(storageKey);
            return value == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(value));
        }

        @Override
        public void deleteIfExists(String storageKey) {
            files.remove(storageKey);
        }
    }

    static final class TestAiOcrClient implements AiOcrClient {
        private final List<AiOcrRequest> requests = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failNext = new AtomicBoolean();

        void reset() {
            requests.clear();
            failNext.set(false);
        }

        void failNext() {
            failNext.set(true);
        }

        List<AiOcrRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public AiOcrResponse recognize(AiOcrRequest request, AiRuntimeCallContext context) {
            requests.add(request);
            if (failNext.compareAndSet(true, false)) {
                throw new AiRuntimeCallException(
                        AiRuntimeFailureCode.RUNTIME_UNAVAILABLE,
                        "AI OCR is unavailable."
                );
            }
            return new AiOcrResponse(
                    request.requestId(),
                    request.workerDocumentId(),
                    AiOcrStatus.SUCCEEDED,
                    43038L,
                    null,
                    Map.of(
                            "passport_number", "M12345678",
                            "surname", "NGUYEN",
                            "given_names", "VAN AN",
                            "date_of_birth", "1995-03-01",
                            "passport_expiry_date", "2028-03-01"
                    ),
                    Map.of(
                            "passport_number", new BigDecimal("0.99"),
                            "surname", new BigDecimal("0.98"),
                            "given_names", new BigDecimal("0.97"),
                            "date_of_birth", new BigDecimal("0.99"),
                            "passport_expiry_date", new BigDecimal("0.96")
                    ),
                    List.of()
            );
        }
    }
}
