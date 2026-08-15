package com.fowoco.server.workerlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.renewal.RenewalRequestedField;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.reliability.application.OutboxProcessor;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsMessage;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsProviderException;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsSender;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkerLinkSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("A0000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("B0000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("A1000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("B1000000-0000-0000-0000-000000000002");
    private static final UUID VIEWER_A = UUID.fromString("A1000000-0000-0000-0000-000000000003");
    private static final String HR_A_EMAIL = "hr.link.a@example.com";
    private static final String HR_B_EMAIL = "hr.link.b@example.com";
    private static final String VIEWER_A_EMAIL = "viewer.link.a@example.com";
    private static final String PASSWORD = "Test-password-1!";
    private static final String BOUNDARY = "FowocoLinkTestBoundary1234";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WorkerLinkHasher workerLinkHasher;

    @MockitoBean
    private WorkerLinkSmsSender workerLinkSmsSender;

    @MockitoBean
    private RenewalRuntimeClient renewalRuntimeClient;

    @Autowired
    private OutboxProcessor outboxProcessor;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedCompaniesAndUsers() {
        cleanupAll();
        insertCompany(COMPANY_A, "사업장 A");
        insertCompany(COMPANY_B, "사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash, "HR");
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash, "HR");
        insertUser(VIEWER_A, COMPANY_A, VIEWER_A_EMAIL, passwordHash, "VIEWER");
    }

    @BeforeEach
    void resetState() {
        reset(renewalRuntimeClient);
        jdbcTemplate.update("DELETE FROM worker_response_upload");
        jdbcTemplate.update("DELETE FROM worker_response");
        jdbcTemplate.update("DELETE FROM worker_document_upload_idempotency");
        jdbcTemplate.update("DELETE FROM worker_link");
        jdbcTemplate.update("DELETE FROM document_request_draft_type");
        jdbcTemplate.update("DELETE FROM document_request_draft");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("UPDATE company_settings SET link_expiry_hours = 72");
    }

    private void cleanupAll() {
        resetState();
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");
    }

    @Test
    void fullFlow_issueViewUploadRespond_succeeds() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "전체흐름테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        saveDocumentRequestDraft(hrToken, taskId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                "fullflow-issue-key"
        );
        assertThat(issueResponse.statusCode()).as("issue response body: %s", issueResponse.body()).isEqualTo(201);
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        String rawToken = JsonPath.read(issueResponse.body(), "$.worker_link_token");
        String workerUrl = JsonPath.read(issueResponse.body(), "$.worker_url");
        assertThat(rawToken).isNotBlank();
        assertThat(workerUrl).isEqualTo("http://localhost:5173/worker-portal/" + rawToken);

        HttpResponse<String> markSentResponse = postWithoutBody(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken
        );
        assertThat(markSentResponse.statusCode()).isEqualTo(200);

        HttpResponse<String> viewResponse = getJson("/api/v1/public/worker-links/" + rawToken, null);
        assertThat(viewResponse.statusCode()).isEqualTo(200);
        assertThat(viewResponse.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(JsonPath.<String>read(viewResponse.body(), "$.guidance"))
                .isEqualTo("여권 사본과 근로계약서를 제출해 주세요.");
        assertThat(JsonPath.<String>read(viewResponse.body(), "$.language")).isEqualTo("vi");
        assertThat(JsonPath.<String>read(viewResponse.body(), "$.due_date")).isEqualTo("2026-08-20");
        assertThat(JsonPath.<List<String>>read(viewResponse.body(), "$.requested_document_types"))
                .containsExactlyInAnyOrder("PASSPORT_COPY", "CONTRACT");
        assertThat(JsonPath.<List<String>>read(viewResponse.body(), "$.requested_actions[*].type"))
                .containsExactly("UPLOAD_DOCUMENT", "UPLOAD_DOCUMENT");

        HttpResponse<String> uploadResponse = uploadFile(rawToken, "passport.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));
        assertThat(uploadResponse.statusCode()).isEqualTo(201);
        String uploadId = JsonPath.read(uploadResponse.body(), "$.upload_id");

        HttpResponse<String> uploadFirstResponse = uploadFileWithIdempotencyKey(
                rawToken, "passport.pdf", "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                "legacy-client-request-id",
                "fixed-upload-idempotency-key"
        );
        assertThat(uploadFirstResponse.statusCode()).isEqualTo(201);
        String firstUploadId = JsonPath.read(uploadFirstResponse.body(), "$.upload_id");

        HttpResponse<String> uploadRetryResponse = uploadFileWithIdempotencyKey(
                rawToken, "passport.pdf", "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                null,
                "fixed-upload-idempotency-key"
        );
        assertThat(uploadRetryResponse.statusCode()).isEqualTo(201);
        String retryUploadId = JsonPath.read(uploadRetryResponse.body(), "$.upload_id");
        assertThat(retryUploadId).isEqualTo(firstUploadId);

        HttpResponse<String> responseSubmit = postJson(
                "/api/v1/public/worker-links/" + rawToken + "/responses",
                """
                {"response_type":"DOCUMENT_SUBMITTED","upload_ids":["%s"],"idempotency_key":"key-1"}
                """.formatted(uploadId),
                null
        );
        assertThat(responseSubmit.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(responseSubmit.body(), "$.response_id")).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT conversation_status FROM worker_link WHERE task_id = ?",
                String.class,
                UUID.fromString(taskId)
        )).isEqualTo("NEEDS_FOLLOWUP");

        HttpResponse<String> activitiesResponse = getJson("/api/v1/tasks/" + taskId + "/activities", hrToken);
        assertThat(activitiesResponse.statusCode()).isEqualTo(200);
        assertThat(activitiesResponse.body()).contains("WORKER_LINK_RESPONSE_SUBMITTED");

        HttpResponse<String> firstWorkerActivityPage = getJson(
                "/api/v1/workers/" + workerId + "/activities?limit=1",
                hrToken
        );
        assertThat(firstWorkerActivityPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(firstWorkerActivityPage.body(), "$.items[*].type"))
                .containsExactly("WORKER_RESPONSE_SUBMITTED");
        assertThat(JsonPath.<String>read(firstWorkerActivityPage.body(), "$.items[0].task_id"))
                .isEqualTo(taskId);
        assertThat(JsonPath.<String>read(firstWorkerActivityPage.body(), "$.items[0].task_title"))
                .isEqualTo("재계약 준비");
        String nextCursor = JsonPath.read(firstWorkerActivityPage.body(), "$.next_cursor");
        assertThat(nextCursor).isNotBlank();

        HttpResponse<String> secondWorkerActivityPage = getJson(
                "/api/v1/workers/" + workerId + "/activities?limit=1&cursor=" + nextCursor,
                hrToken
        );
        assertThat(secondWorkerActivityPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(secondWorkerActivityPage.body(), "$.items[*].type"))
                .containsExactly("GUIDANCE_OPENED");
        assertThat(secondWorkerActivityPage.body())
                .doesNotContain("request_id")
                .doesNotContain("trace_id")
                .doesNotContain(rawToken);

        HttpResponse<String> viewerActivities = getJson(
                "/api/v1/workers/" + workerId + "/activities",
                accessToken(login(VIEWER_A_EMAIL))
        );
        assertThat(viewerActivities.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(viewerActivities.body(), "$.items[*].type"))
                .containsExactly("WORKER_RESPONSE_SUBMITTED", "GUIDANCE_OPENED", "GUIDANCE_SENT");

        HttpResponse<String> otherCompanyActivities = getJson(
                "/api/v1/workers/" + workerId + "/activities",
                accessToken(login(HR_B_EMAIL))
        );
        assertThat(otherCompanyActivities.statusCode()).isEqualTo(404);
    }

    @Test
    void documentUploadUsesCanonicalIdempotencyKeyAndRejectsDifferentReplay() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "업로드멱등성테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        saveDocumentRequestDraft(hrToken, taskId);
        String rawToken = issueWorkerLink(hrToken, taskId, "document-upload-idempotency-link-key");

        HttpResponse<String> missingHeader = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                "legacy-request-id",
                null
        );
        assertThat(missingHeader.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(missingHeader.body(), "$.code")).isEqualTo("VALIDATION_FAILED");

        HttpResponse<String> invalidHeader = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                null,
                "short"
        );
        assertThat(invalidHeader.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(invalidHeader.body(), "$.code")).isEqualTo("VALIDATION_FAILED");

        HttpResponse<String> invalidDocumentType = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                null,
                "canonical-invalid-document-type",
                "CUSTOM_DOCUMENT"
        );
        assertThat(invalidDocumentType.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(invalidDocumentType.body(), "$.code"))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ?",
                Integer.class,
                COMPANY_A
        )).isZero();

        HttpResponse<String> first = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                "shared-legacy-request-id",
                "canonical-upload-key-1"
        );
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        String firstUploadId = JsonPath.read(first.body(), "$.upload_id");

        HttpResponse<String> retryWithoutLegacyField = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                null,
                "canonical-upload-key-1"
        );
        assertThat(retryWithoutLegacyField.statusCode()).as(retryWithoutLegacyField.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(retryWithoutLegacyField.body(), "$.upload_id"))
                .isEqualTo(firstUploadId);

        HttpResponse<String> conflict = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "different-content".getBytes(StandardCharsets.UTF_8),
                "shared-legacy-request-id",
                "canonical-upload-key-1"
        );
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(conflict.body(), "$.code")).isEqualTo("IDEMPOTENCY_CONFLICT");

        HttpResponse<String> differentCanonicalKey = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8),
                "shared-legacy-request-id",
                "canonical-upload-key-2"
        );
        assertThat(differentCanonicalKey.statusCode()).as(differentCanonicalKey.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(differentCanonicalKey.body(), "$.upload_id"))
                .isNotEqualTo(firstUploadId);

        UUID workerLinkId = jdbcTemplate.queryForObject(
                "SELECT worker_link_id FROM worker_link WHERE task_id = ?",
                UUID.class,
                UUID.fromString(taskId)
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document_upload_idempotency WHERE worker_link_id = ?",
                Integer.class,
                workerLinkId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT client_request_id) FROM worker_document_upload_idempotency "
                        + "WHERE worker_link_id = ? "
                        + "AND client_request_id = CONCAT('canonical:', CAST(stored_file_id AS VARCHAR)) "
                        + "AND idempotency_key_hash IS NOT NULL AND request_hash IS NOT NULL",
                Integer.class,
                workerLinkId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ?",
                Integer.class,
                COMPANY_A
        )).isEqualTo(2);
    }

    @Test
    void canonicalUploadDoesNotCollideWithLegacyClientRequestIdEqualToItsKeyHash() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "legacy충돌테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        saveDocumentRequestDraft(hrToken, taskId);
        String rawToken = issueWorkerLink(hrToken, taskId, "legacy-collision-link-key");
        UUID workerLinkId = jdbcTemplate.queryForObject(
                "SELECT worker_link_id FROM worker_link WHERE task_id = ?",
                UUID.class,
                UUID.fromString(taskId)
        );
        String idempotencyKey = "legacy-collision-upload-key";
        String idempotencyKeyHash = workerLinkHasher.hash(idempotencyKey);
        UUID legacyStoredFileId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO stored_file (
                    stored_file_id, company_id, name, mime_type, size, purpose,
                    task_id, storage_key, scan_status, verified
                ) VALUES (?, ?, 'legacy.pdf', 'application/pdf', 1, 'WORKER_LINK_SUBMISSION',
                          ?, ?, 'NOT_SCANNED', FALSE)
                """,
                legacyStoredFileId,
                COMPANY_A,
                UUID.fromString(taskId),
                "legacy-collision-" + legacyStoredFileId
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker_document_upload_idempotency (
                    worker_link_id, company_id, client_request_id, stored_file_id
                ) VALUES (?, ?, ?, ?)
                """,
                workerLinkId,
                COMPANY_A,
                idempotencyKeyHash,
                legacyStoredFileId
        );

        HttpResponse<String> response = uploadFileWithIdempotencyKey(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "canonical-content".getBytes(StandardCharsets.UTF_8),
                null,
                idempotencyKey
        );

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        UUID canonicalStoredFileId = UUID.fromString(JsonPath.read(response.body(), "$.upload_id"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT client_request_id FROM worker_document_upload_idempotency "
                        + "WHERE worker_link_id = ? AND idempotency_key_hash = ?",
                String.class,
                workerLinkId,
                idempotencyKeyHash
        )).isEqualTo("canonical:" + canonicalStoredFileId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document_upload_idempotency WHERE worker_link_id = ?",
                Integer.class,
                workerLinkId
        )).isEqualTo(2);
    }

    @Test
    void requestedSlotAnswerIsStoredAndDifferentIdempotentPayloadIsRejected() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "구조화답변테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        jdbcTemplate.update(
                "UPDATE task SET business_data_json = ? WHERE task_id = ?",
                """
                {
                  "monthly_wage":2500000,
                  "renewal_execution":{
                    "requested_fields":[
                      {"key":"lodging","source_hint":"USER_INPUT"},
                      {"key":"passport_number","source_hint":"DOCUMENT_OCR"}
                    ]
                  }
                }
                """,
                UUID.fromString(taskId)
        );
        saveDocumentRequestDraft(hrToken, taskId);
        String rawToken = issueWorkerLink(hrToken, taskId, "slot-answer-link-key");

        HttpResponse<String> viewResponse = getJson(
                "/api/v1/public/worker-links/" + rawToken,
                null
        );
        assertThat(viewResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(viewResponse.body(), "$.requested_actions[*].type"))
                .containsExactly("ANSWER_FIELD", "UPLOAD_DOCUMENT", "UPLOAD_DOCUMENT");
        assertThat(JsonPath.<List<String>>read(viewResponse.body(), "$.requested_actions[*].field_key"))
                .contains("lodging");
        assertThat(viewResponse.body()).doesNotContain("passport_number");

        String body = """
                {
                  "response_type":"SLOT_ANSWERS_SUBMITTED",
                  "answers":{"lodging":"사업장 건물 숙소 제공"},
                  "idempotency_key":"slot-answer-1"
                }
                """;
        HttpResponse<String> submitted = postJson(
                "/api/v1/public/worker-links/" + rawToken + "/responses",
                body,
                null
        );
        assertThat(submitted.statusCode()).as(submitted.body()).isEqualTo(201);
        String responseId = JsonPath.read(submitted.body(), "$.response_id");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT answers_json FROM worker_response WHERE response_id = ?",
                String.class,
                UUID.fromString(responseId)
        )).contains("사업장 건물 숙소 제공");

        doAnswer(invocation -> continuationResponse(invocation.getArgument(0)))
                .when(renewalRuntimeClient).run(any(), any());
        assertThat(outboxProcessor.processAvailable()).isGreaterThanOrEqualTo(1);
        ArgumentCaptor<RenewalRunRequest> renewalRequest =
                ArgumentCaptor.forClass(RenewalRunRequest.class);
        verify(renewalRuntimeClient).run(renewalRequest.capture(), any());
        assertThat(renewalRequest.getValue().slots())
                .containsEntry("lodging", "사업장 건물 숙소 제공");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT business_data_json FROM task WHERE task_id = ?",
                String.class,
                UUID.fromString(taskId)
        )).contains("renewal_inputs", "사업장 건물 숙소 제공");
        assertThat(outboxProcessor.processAvailable()).isZero();

        HttpResponse<String> retry = postJson(
                "/api/v1/public/worker-links/" + rawToken + "/responses",
                body,
                null
        );
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(retry.body(), "$.response_id")).isEqualTo(responseId);

        HttpResponse<String> conflict = postJson(
                "/api/v1/public/worker-links/" + rawToken + "/responses",
                """
                {
                  "response_type":"SLOT_ANSWERS_SUBMITTED",
                  "answers":{"lodging":"다른 숙소 조건"},
                  "idempotency_key":"slot-answer-1"
                }
                """,
                null
        );
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("WORKER_RESPONSE_IDEMPOTENCY_CONFLICT");

        HttpResponse<String> page = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses",
                hrToken
        );
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(page.body(), "$.items[0].answers.lodging"))
                .isEqualTo("사업장 건물 숙소 제공");
    }

    private RenewalRunResponse continuationResponse(RenewalRunRequest request) {
        return new RenewalRunResponse(
                request.requestId(),
                request.attemptId(),
                request.taskId(),
                "EXPIRY_RENEWAL",
                request.task().workflowId(),
                new BigDecimal("0.91"),
                "NEEDS_INFO",
                "NEEDS_INFO",
                "ask_hr",
                "PHASE_1",
                "STEP_2",
                Map.of(),
                List.of("wage"),
                List.of(new RenewalRequestedField("wage", "USER_INPUT")),
                null,
                null,
                false,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                "rules",
                "main",
                List.of()
        );
    }

    @Test
    void unrequestedOrSensitiveSlotAnswerIsRejected() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "미요청답변차단근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        saveDocumentRequestDraft(hrToken, taskId);
        String rawToken = issueWorkerLink(hrToken, taskId, "slot-answer-reject-link-key");

        HttpResponse<String> response = postJson(
                "/api/v1/public/worker-links/" + rawToken + "/responses",
                """
                {
                  "response_type":"SLOT_ANSWERS_SUBMITTED",
                  "answers":{"passport_number":"M12345678"},
                  "idempotency_key":"slot-answer-reject-1"
                }
                """,
                null
        );

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("WORKER_SLOT_ANSWER_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_response",
                Integer.class
        )).isZero();
    }

    @Test
    void workerWithoutGuidanceReturnsEmptyActivityPage() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "안내이력없는근로자");

        HttpResponse<String> response = getJson(
                "/api/v1/workers/" + workerId + "/activities",
                hrToken
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<Object>>read(response.body(), "$.items")).isEmpty();
        assertThat(JsonPath.<Object>read(response.body(), "$.next_cursor")).isNull();
    }

    @Test
    void issueUsesCompanyDefaultExpiryAndExplicitRequestTakesPrecedence() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        jdbcTemplate.update(
                "UPDATE company_settings SET link_expiry_hours = 24 WHERE company_id = ?",
                COMPANY_A
        );

        String defaultWorkerId = registerWorker(hrToken, "회사기본만료시간근로자");
        String defaultTaskId = createApprovedTask(hrToken, defaultWorkerId);
        Instant beforeDefaultIssue = Instant.now();
        HttpResponse<String> defaultResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + defaultTaskId + "/worker-link",
                """
                {"rotate_existing":false}
                """,
                hrToken,
                "company-default-expiry-key"
        );
        Instant afterDefaultIssue = Instant.now();

        assertThat(defaultResponse.statusCode())
                .as("default expiry response body: %s", defaultResponse.body())
                .isEqualTo(201);
        Instant defaultExpiresAt = Instant.parse(
                JsonPath.read(defaultResponse.body(), "$.expires_at")
        );
        assertThat(defaultExpiresAt)
                .isBetween(beforeDefaultIssue.plusSeconds(24L * 60L * 60L)
                                .truncatedTo(ChronoUnit.MICROS),
                        afterDefaultIssue.plusSeconds(24L * 60L * 60L));

        String explicitWorkerId = registerWorker(hrToken, "요청만료시간근로자");
        String explicitTaskId = createApprovedTask(hrToken, explicitWorkerId);
        Instant beforeExplicitIssue = Instant.now();
        HttpResponse<String> explicitResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + explicitTaskId + "/worker-link",
                """
                {"expires_in_hours":12,"rotate_existing":false}
                """,
                hrToken,
                "explicit-expiry-key"
        );
        Instant afterExplicitIssue = Instant.now();

        assertThat(explicitResponse.statusCode())
                .as("explicit expiry response body: %s", explicitResponse.body())
                .isEqualTo(201);
        Instant explicitExpiresAt = Instant.parse(
                JsonPath.read(explicitResponse.body(), "$.expires_at")
        );
        assertThat(explicitExpiresAt)
                .isBetween(beforeExplicitIssue.plusSeconds(12L * 60L * 60L)
                                .truncatedTo(ChronoUnit.MICROS),
                        afterExplicitIssue.plusSeconds(12L * 60L * 60L));
    }

    @Test
    void issueRejectsExpiryOutsideMvpRange() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "만료시간검증근로자");
        String taskId = createApprovedTask(hrToken, workerId);

        assertThat(postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                "{\"expires_in_hours\":0,\"rotate_existing\":false}",
                hrToken,
                "expiry-too-small-key"
        ).statusCode()).isEqualTo(400);
        assertThat(postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                "{\"expires_in_hours\":169,\"rotate_existing\":false}",
                hrToken,
                "expiry-too-large-key"
        ).statusCode()).isEqualTo(400);
    }

    @Test
    void activeWorkerLinkWithoutDraftReturnsContentNotReady() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "초안없는링크근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String workerUrl = issueWorkerLink(hrToken, taskId, "content-not-ready-key");

        HttpResponse<String> response = getJson("/api/v1/public/worker-links/" + workerUrl, null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("WORKER_LINK_CONTENT_NOT_READY");
    }

    @Test
    void hrCanListAndMarkWorkerResponsesReviewed() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "응답조회테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String workerUrl = issueWorkerLink(hrToken, taskId, "response-management-key");

        HttpResponse<String> submitResponse = postJson(
                "/api/v1/public/worker-links/" + workerUrl + "/responses",
                """
                {"response_type":"QUESTION","message":"여권의 어느 면을 제출하나요?","idempotency_key":"question-key"}
                """,
                null
        );
        assertThat(submitResponse.statusCode()).isEqualTo(201);

        HttpResponse<String> listResponse = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses?page=0&size=20",
                hrToken
        );
        assertThat(listResponse.statusCode()).as("body: %s", listResponse.body()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(listResponse.body(), "$.total_elements")).isEqualTo(1);
        assertThat(JsonPath.<String>read(listResponse.body(), "$.items[0].response_type"))
                .isEqualTo("QUESTION");
        assertThat(JsonPath.<String>read(listResponse.body(), "$.items[0].message"))
                .isEqualTo("여권의 어느 면을 제출하나요?");
        assertThat(JsonPath.<Boolean>read(listResponse.body(), "$.items[0].unread")).isTrue();

        HttpResponse<String> readResponse = postWithoutBody(
                "/api/v1/tasks/" + taskId + "/worker-responses/read",
                hrToken
        );
        assertThat(readResponse.statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT conversation_status FROM worker_link WHERE task_id = ?",
                String.class,
                UUID.fromString(taskId)
        )).isEqualTo("REOPENED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_RESPONSES_REVIEWED'",
                Integer.class,
                UUID.fromString(taskId)
        )).isEqualTo(1);

        HttpResponse<String> reviewedListResponse = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses",
                hrToken
        );
        assertThat(JsonPath.<Boolean>read(reviewedListResponse.body(), "$.items[0].unread")).isFalse();

        HttpResponse<String> repeatedReadResponse = postWithoutBody(
                "/api/v1/tasks/" + taskId + "/worker-responses/read",
                hrToken
        );
        assertThat(repeatedReadResponse.statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_RESPONSES_REVIEWED'",
                Integer.class,
                UUID.fromString(taskId)
        )).isEqualTo(1);
    }

    @Test
    void hrCanInspectAndAdoptSubmittedFilesThenResumeTask() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "제출서류채택테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        saveDocumentRequestDraft(hrToken, taskId);
        String rawToken = issueWorkerLink(hrToken, taskId, "document-adoption-link-key");

        HttpResponse<String> passportUpload = uploadFileAsType(
                rawToken,
                "passport.pdf",
                "application/pdf",
                "passport-content".getBytes(StandardCharsets.UTF_8),
                "PASSPORT_COPY"
        );
        HttpResponse<String> contractUpload = uploadFileAsType(
                rawToken,
                "contract.pdf",
                "application/pdf",
                "contract-content".getBytes(StandardCharsets.UTF_8),
                "CONTRACT"
        );
        assertThat(passportUpload.statusCode()).isEqualTo(201);
        assertThat(contractUpload.statusCode()).isEqualTo(201);
        String passportFileId = JsonPath.read(passportUpload.body(), "$.upload_id");
        String contractFileId = JsonPath.read(contractUpload.body(), "$.upload_id");

        HttpResponse<String> submitResponse = postJson(
                "/api/v1/public/worker-links/" + rawToken + "/responses",
                """
                {
                  "response_type":"DOCUMENT_SUBMITTED",
                  "message":"요청하신 서류를 제출합니다.",
                  "upload_ids":["%s","%s"],
                  "idempotency_key":"document-adoption-response-key"
                }
                """.formatted(passportFileId, contractFileId),
                null
        );
        assertThat(submitResponse.statusCode()).isEqualTo(201);
        String responseId = JsonPath.read(submitResponse.body(), "$.response_id");

        HttpResponse<String> beforeAdoption = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses",
                hrToken
        );
        assertThat(beforeAdoption.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(beforeAdoption.body(), "$.items[0].message"))
                .isEqualTo("요청하신 서류를 제출합니다.");
        assertThat(JsonPath.<List<String>>read(beforeAdoption.body(), "$.items[0].uploads[*].file_name"))
                .containsExactlyInAnyOrder("passport.pdf", "contract.pdf");
        assertThat(JsonPath.<List<Boolean>>read(beforeAdoption.body(), "$.items[0].uploads[*].adopted"))
                .containsOnly(false);

        HttpResponse<String> taskBeforeAdoption = getJson("/api/v1/tasks/" + taskId, hrToken);
        assertThat(JsonPath.<String>read(taskBeforeAdoption.body(), "$.status"))
                .isEqualTo("WAITING_WORKER");
        int expectedTaskVersion = ((Number) JsonPath.read(taskBeforeAdoption.body(), "$.version")).intValue();

        String otherCompanyHrToken = accessToken(login(HR_B_EMAIL));
        HttpResponse<String> crossTenantAdoption = postJson(
                "/api/v1/tasks/" + taskId + "/worker-responses/" + responseId + "/documents/adopt",
                """
                {"expected_task_version":%d}
                """.formatted(expectedTaskVersion),
                otherCompanyHrToken
        );
        assertThat(crossTenantAdoption.statusCode()).isEqualTo(404);

        HttpResponse<String> adoption = postJson(
                "/api/v1/tasks/" + taskId + "/worker-responses/" + responseId + "/documents/adopt",
                """
                {"expected_task_version":%d}
                """.formatted(expectedTaskVersion),
                hrToken
        );

        assertThat(adoption.statusCode()).as("adoption response body: %s", adoption.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(adoption.body(), "$.task_status")).isEqualTo("APPROVED");
        assertThat(JsonPath.<List<String>>read(adoption.body(), "$.adopted_documents[*].document_type"))
                .containsExactlyInAnyOrder("PASSPORT_COPY", "CONTRACT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE task_id = ? AND submission_status = 'SUBMITTED'",
                Integer.class,
                UUID.fromString(taskId)
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication "
                        + "WHERE event_type = 'WorkerDocumentAdopted'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM worker_link WHERE task_id = ?",
                String.class,
                UUID.fromString(taskId)
        )).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT conversation_status FROM worker_link WHERE task_id = ?",
                String.class,
                UUID.fromString(taskId)
        )).isEqualTo("REOPENED");

        HttpResponse<String> afterAdoption = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses",
                hrToken
        );
        assertThat(JsonPath.<List<Boolean>>read(afterAdoption.body(), "$.items[0].uploads[*].adopted"))
                .containsOnly(true);

        int currentTaskVersion = ((Number) JsonPath.read(adoption.body(), "$.task_version")).intValue();
        HttpResponse<String> repeated = postJson(
                "/api/v1/tasks/" + taskId + "/worker-responses/" + responseId + "/documents/adopt",
                """
                {"expected_task_version":%d}
                """.formatted(currentTaskVersion),
                hrToken
        );
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE task_id = ?",
                Integer.class,
                UUID.fromString(taskId)
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication "
                        + "WHERE event_type = 'WorkerDocumentAdopted'",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void workerResponsesAreHiddenFromOtherCompany() throws Exception {
        String hrTokenA = accessToken(login(HR_A_EMAIL));
        String hrTokenB = accessToken(login(HR_B_EMAIL));
        String workerId = registerWorker(hrTokenA, "응답격리테스트근로자");
        String taskId = createApprovedTask(hrTokenA, workerId);

        HttpResponse<String> listResponse = getJson(
                "/api/v1/tasks/" + taskId + "/worker-responses",
                hrTokenB
        );
        assertThat(listResponse.statusCode()).isEqualTo(404);

        HttpResponse<String> readResponse = postWithoutBody(
                "/api/v1/tasks/" + taskId + "/worker-responses/read",
                hrTokenB
        );
        assertThat(readResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void hrCanQueryAndMarkWorkerLinkSentIdempotently() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "링크전달테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                "delivery-issue-key"
        );
        assertThat(issueResponse.statusCode()).isEqualTo(201);
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        assertThat(JsonPath.<String>read(issueResponse.body(), "$.delivery_status"))
                .isEqualTo("NOT_SENT");
        assertThat(JsonPath.<Object>read(issueResponse.body(), "$.sent_at")).isNull();

        HttpResponse<String> beforeSent = getJson(
                "/api/v1/tasks/" + taskId + "/worker-link",
                hrToken
        );
        assertThat(beforeSent.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(beforeSent.body(), "$.worker_link_id"))
                .isEqualTo(workerLinkId);
        assertThat(JsonPath.<String>read(beforeSent.body(), "$.delivery_status"))
                .isEqualTo("NOT_SENT");

        HttpResponse<String> sent = postWithoutBody(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken
        );
        assertThat(sent.statusCode()).as("sent response body: %s", sent.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(sent.body(), "$.delivery_status")).isEqualTo("SENT");
        assertThat(JsonPath.<String>read(sent.body(), "$.sent_at")).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sent_by FROM worker_link WHERE worker_link_id = ?",
                UUID.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo(HR_A);

        HttpResponse<String> firstRetry = postWithoutBody(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken
        );
        HttpResponse<String> secondRetry = postWithoutBody(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrToken
        );
        assertThat(firstRetry.statusCode()).isEqualTo(200);
        assertThat(secondRetry.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(firstRetry.body(), "$.sent_at"))
                .isEqualTo(JsonPath.read(sent.body(), "$.sent_at"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_SENT'",
                Integer.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo(1);
    }

    @Test
    void hrCanSendWorkerLinkBySmsAndRetryWithoutDuplicateDelivery() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "SMS발송테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String issuanceKey = "sms-delivery-issue-key";
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                issuanceKey
        );
        assertThat(issueResponse.statusCode()).isEqualTo(201);
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        String rawToken = JsonPath.read(issueResponse.body(), "$.worker_link_token");
        String body = """
                {"recipient_phone":"+82 10-1234-5678","worker_link_token":"%s"}
                """.formatted(rawToken);

        HttpResponse<String> first = postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                body,
                hrToken,
                issuanceKey
        );
        HttpResponse<String> retry = postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                body,
                hrToken,
                issuanceKey
        );

        assertThat(first.statusCode()).as("SMS response body: %s", first.body()).isEqualTo(200);
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(first.body(), "$.delivery_status")).isEqualTo("SENT");
        assertThat(JsonPath.<String>read(retry.body(), "$.sent_at"))
                .isEqualTo(JsonPath.read(first.body(), "$.sent_at"));

        ArgumentCaptor<WorkerLinkSmsMessage> messageCaptor =
                ArgumentCaptor.forClass(WorkerLinkSmsMessage.class);
        verify(workerLinkSmsSender, times(1)).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().recipientPhone()).isEqualTo("01012345678");
        assertThat(messageCaptor.getValue().content())
                .contains("http://localhost:5173/worker-portal/" + rawToken)
                .doesNotContain("01012345678");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_SENT'",
                Integer.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo(1);
    }

    @Test
    void smsProviderFailureKeepsWorkerLinkNotSent() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "SMS실패테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String issuanceKey = "sms-delivery-failure-key";
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                issuanceKey
        );
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        String rawToken = JsonPath.read(issueResponse.body(), "$.worker_link_token");
        doThrow(WorkerLinkSmsProviderException.rejected(null))
                .when(workerLinkSmsSender).send(any(WorkerLinkSmsMessage.class));

        HttpResponse<String> response = postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                """
                {"recipient_phone":"010-1234-5678","worker_link_token":"%s"}
                """.formatted(rawToken),
                hrToken,
                issuanceKey
        );

        assertThat(response.statusCode()).as("SMS failure body: %s", response.body()).isEqualTo(502);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM worker_link WHERE worker_link_id = ?",
                String.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo("NOT_SENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_LINK_SENT'",
                Integer.class,
                UUID.fromString(workerLinkId)
        )).isZero();
    }

    @Test
    void smsDeliveryCommitsSendingBeforeProviderAndDoesNotRecheckExpiryAfterAcceptance() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "SMS만료경계테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String issuanceKey = "sms-delivery-expiry-boundary-key";
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                issuanceKey
        );
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        String rawToken = JsonPath.read(issueResponse.body(), "$.worker_link_token");
        UUID linkId = UUID.fromString(workerLinkId);
        doAnswer(invocation -> {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT delivery_status FROM worker_link WHERE worker_link_id = ?",
                    String.class,
                    linkId
            )).isEqualTo("SENDING");
            Instant createdAt = jdbcTemplate.queryForObject(
                    "SELECT created_at FROM worker_link WHERE worker_link_id = ?",
                    Instant.class,
                    linkId
            );
            jdbcTemplate.update(
                    "UPDATE worker_link SET expires_at = ? WHERE worker_link_id = ?",
                    createdAt.plusMillis(1),
                    linkId
            );
            return null;
        }).when(workerLinkSmsSender).send(any(WorkerLinkSmsMessage.class));

        HttpResponse<String> response = postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                """
                {"recipient_phone":"010-1234-5678","worker_link_token":"%s"}
                """.formatted(rawToken),
                hrToken,
                issuanceKey
        );

        assertThat(response.statusCode()).as("SMS expiry boundary body: %s", response.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.delivery_status")).isEqualTo("SENT");
        verify(workerLinkSmsSender, times(1)).send(any(WorkerLinkSmsMessage.class));
    }

    @Test
    void unknownSmsResultRequiresReviewAndBlocksAutomaticRetry() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "SMS결과확인테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String issuanceKey = "sms-delivery-unknown-key";
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                issuanceKey
        );
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        String rawToken = JsonPath.read(issueResponse.body(), "$.worker_link_token");
        doThrow(WorkerLinkSmsProviderException.unknown(null))
                .when(workerLinkSmsSender).send(any(WorkerLinkSmsMessage.class));
        String body = """
                {"recipient_phone":"010-1234-5678","worker_link_token":"%s"}
                """.formatted(rawToken);

        HttpResponse<String> first = postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                body,
                hrToken,
                issuanceKey
        );
        HttpResponse<String> retry = postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                body,
                hrToken,
                issuanceKey
        );

        assertThat(first.statusCode()).isEqualTo(409);
        assertThat(retry.statusCode()).isEqualTo(409);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM worker_link WHERE worker_link_id = ?",
                String.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? "
                        + "AND action = 'WORKER_LINK_SMS_DELIVERY_REVIEW_REQUIRED'",
                Integer.class,
                UUID.fromString(workerLinkId)
        )).isEqualTo(1);
        verify(workerLinkSmsSender, times(1)).send(any(WorkerLinkSmsMessage.class));
    }

    @Test
    void smsDeliveryRejectsTokenMismatchWithoutCallingProvider() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "SMS위변조테스트근로자");
        String taskId = createApprovedTask(hrToken, workerId);
        String issuanceKey = "sms-delivery-mismatch-key";
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                issuanceKey
        );
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");

        HttpResponse<String> response = postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                """
                {"recipient_phone":"01012345678","worker_link_token":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                """,
                hrToken,
                issuanceKey
        );

        assertThat(response.statusCode()).isEqualTo(409);
        verify(workerLinkSmsSender, never()).send(any(WorkerLinkSmsMessage.class));
    }

    @Test
    void deliveryEndpointsHideOtherCompanyAndRejectViewer() throws Exception {
        String hrTokenA = accessToken(login(HR_A_EMAIL));
        String hrTokenB = accessToken(login(HR_B_EMAIL));
        String viewerTokenA = accessToken(login(VIEWER_A_EMAIL));
        String workerId = registerWorker(hrTokenA, "링크권한테스트근로자");
        String taskId = createApprovedTask(hrTokenA, workerId);
        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrTokenA,
                "delivery-security-issue-key"
        );
        String workerLinkId = JsonPath.read(issueResponse.body(), "$.worker_link_id");
        String rawToken = JsonPath.read(issueResponse.body(), "$.worker_link_token");
        String smsBody = """
                {"recipient_phone":"01012345678","worker_link_token":"%s"}
                """.formatted(rawToken);

        assertThat(getJson("/api/v1/tasks/" + taskId + "/worker-link", hrTokenB).statusCode())
                .isEqualTo(404);
        assertThat(postWithoutBody(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                hrTokenB
        ).statusCode()).isEqualTo(404);
        assertThat(postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                smsBody,
                hrTokenB,
                "delivery-security-issue-key"
        ).statusCode()).isEqualTo(404);
        assertThat(getJson("/api/v1/tasks/" + taskId + "/worker-link", viewerTokenA).statusCode())
                .isEqualTo(403);
        assertThat(postWithoutBody(
                "/api/v1/worker-links/" + workerLinkId + "/sent",
                viewerTokenA
        ).statusCode()).isEqualTo(403);
        assertThat(postJsonWithIdempotencyKey(
                "/api/v1/worker-links/" + workerLinkId + "/sms-deliveries",
                smsBody,
                viewerTokenA,
                "delivery-security-issue-key"
        ).statusCode()).isEqualTo(403);
        verify(workerLinkSmsSender, never()).send(any(WorkerLinkSmsMessage.class));
    }

    @Test
    void documentsWorkerLinkAndWorkerActivityEndpointsInOpenApi() throws Exception {
        HttpResponse<String> response = getJson("/v3/api-docs", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/tasks/{taskId}/worker-link'].get.operationId"
        )).isEqualTo("getTaskWorkerLinkDelivery");
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/worker-links/{workerLinkId}/sent'].post.operationId"
        )).isEqualTo("markWorkerLinkSent");
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/worker-links/{workerLinkId}/sms-deliveries'].post.operationId"
        )).isEqualTo("sendWorkerLinkSms");
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/workers/{workerId}/activities'].get.operationId"
        )).isEqualTo("getWorkerActivities");
        assertThat(JsonPath.<String>read(
                response.body(),
                "$.paths['/api/v1/public/worker-links/{token}/documents'].post.operationId"
        )).isEqualTo("uploadWorkerLinkDocument");
        assertThat(JsonPath.<List<Boolean>>read(
                response.body(),
                "$.paths['/api/v1/public/worker-links/{token}/documents'].post.parameters"
                        + "[?(@.name == 'Idempotency-Key')].required"
        )).containsExactly(true);
        assertThat(JsonPath.<List<List<String>>>read(
                response.body(),
                "$.paths['/api/v1/public/worker-links/{token}/documents'].post.parameters"
                        + "[?(@.name == 'documentType')].schema.enum"
        )).containsExactly(List.of(
                "PASSPORT_COPY",
                "ARC",
                "CONTRACT",
                "PERMIT",
                "EMPLOYMENT_EXTENSION_APPLICATION",
                "INTEGRATED_APPLICATION",
                "RESIDENCE_PROOF"
        ));
        assertThat(JsonPath.<Number>read(
                response.body(),
                "$.components.schemas.WorkerLinkIssueRequest.properties.expires_in_hours.minimum"
        ).longValue()).isEqualTo(1L);
        assertThat(JsonPath.<Number>read(
                response.body(),
                "$.components.schemas.WorkerLinkIssueRequest.properties.expires_in_hours.maximum"
        ).longValue()).isEqualTo(168L);
    }

    @Test
    void issueRejectsUnapprovedTask() throws Exception {
        String hrToken = accessToken(login(HR_A_EMAIL));
        String workerId = registerWorker(hrToken, "미승인테스트근로자");
        String taskId = createUnapprovedTask(hrToken, workerId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrToken,
                "unapproved-issue-key"
        );

        assertThat(issueResponse.statusCode()).isEqualTo(422);
    }

    @Test
    void issueRejectsOtherCompanyTask() throws Exception {
        String hrTokenA = accessToken(login(HR_A_EMAIL));
        String hrTokenB = accessToken(login(HR_B_EMAIL));
        String workerId = registerWorker(hrTokenA, "타사업장테스트근로자");
        String taskId = createApprovedTask(hrTokenA, workerId);

        HttpResponse<String> issueResponse = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                hrTokenB,
                "othercompany-issue-key"
        );

        assertThat(issueResponse.statusCode()).isEqualTo(404);
    }

    @Test
    void viewReturns410ForNonExistentToken() throws Exception {
        HttpResponse<String> viewResponse = getJson("/api/v1/public/worker-links/nonexistenttoken12345", null);
        assertThat(viewResponse.statusCode()).isEqualTo(410);
    }
    @Test
    void documentsEndpointAllowsIdempotencyKeyHeaderInCors() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/public/worker-links/test-token/documents"))
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Idempotency-Key")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers"))
                .hasValueSatisfying(headers -> assertThat(headers).contains("Idempotency-Key"));
    }

    private String registerWorker(String token, String displayName) throws Exception {
        String body = """
                {"display_name":"%s"}
                """.formatted(displayName);
        HttpResponse<String> response = postJson("/api/v1/workers", body, token);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_id");
    }

    private String createApprovedTask(String token, String workerId) throws Exception {
        String taskId = createUnapprovedTask(token, workerId);
        completeRequiredChecklistItems(taskId, token);

        String approvalBody = """
                {
                  "expected_version":0,
                  "ai_snapshot":{"intent":"EXPIRY_RENEWAL","confidence":0.94},
                  "hr_snapshot":{"worker_id":"%s","contract_end_date":"2027-08-31","monthly_wage":2500000},
                  "changed_fields":["monthly_wage"],
                  "source_versions":{"agent_version":"agent-1","workflow_catalog_version":"2026.07"}
                }
                """.formatted(workerId);
        HttpResponse<String> approvalRequest = postJson(
                "/api/v1/tasks/" + taskId + "/approval-requests", approvalBody, token
        );
        assertThat(approvalRequest.statusCode()).as("approval response body: %s", approvalRequest.body()).isEqualTo(201);

        HttpResponse<String> approve = postJson(
                "/api/v1/tasks/" + taskId + "/approve",
                """
                {"expected_version":1,"reason":"테스트 승인"}
                """,
                token
        );
        assertThat(approve.statusCode()).as("approve response body: %s", approve.body()).isEqualTo(200);
        return taskId;
    }

    private String issueWorkerLink(String token, String taskId, String idempotencyKey) throws Exception {
        HttpResponse<String> response = postJsonWithIdempotencyKey(
                "/api/v1/tasks/" + taskId + "/worker-link",
                """
                {"expires_in_hours":72,"rotate_existing":false}
                """,
                token,
                idempotencyKey
        );
        assertThat(response.statusCode()).as("issue response body: %s", response.body()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.worker_link_token");
    }

    private void saveDocumentRequestDraft(String token, String taskId) throws Exception {
        HttpResponse<String> response = putJson(
                "/api/v1/tasks/" + taskId + "/document-request-draft",
                """
                {
                  "language":"vi",
                  "document_types":["PASSPORT_COPY","CONTRACT"],
                  "message":"여권 사본과 근로계약서를 제출해 주세요.",
                  "expected_version":0
                }
                """,
                token
        );
        assertThat(response.statusCode()).as("draft response body: %s", response.body()).isEqualTo(200);
    }

    private void completeRequiredChecklistItems(String taskId, String token) throws Exception {
        HttpResponse<String> detail = getJson("/api/v1/tasks/" + taskId, token);
        assertThat(detail.statusCode()).isEqualTo(200);

        List<Map<String, Object>> checklistItems = JsonPath.read(detail.body(), "$.checklist_items");
        for (Map<String, Object> item : checklistItems) {
            boolean required = (boolean) item.get("required");
            if (!required) {
                continue;
            }
            String itemId = (String) item.get("checklist_item_id");
            int itemVersion = (int) item.get("version");
            int taskVersion = ((Number) JsonPath.read(detail.body(), "$.version")).intValue();

            HttpResponse<String> patchResponse = patchJson(
                    "/api/v1/tasks/" + taskId + "/checklist-items/" + itemId,
                    """
                    {"completed":true,"expected_version":%d,"expected_task_version":%d}
                    """.formatted(itemVersion, taskVersion),
                    token
            );
            assertThat(patchResponse.statusCode())
                    .as("checklist patch response: %s", patchResponse.body())
                    .isEqualTo(200);
        }
    }

    private String createUnapprovedTask(String token, String workerId) throws Exception {
        String body = """
                {
                  "worker_id":"%s",
                  "task_type":"RECONTRACT",
                  "workflow_id":"WF-CON-001",
                  "title":"재계약 준비",
                  "description":"기존 조건 확인",
                  "due_date":"2026-08-20",
                  "business_data":{"monthly_wage":2500000}
                }
                """.formatted(workerId);
        HttpResponse<String> response = postJson("/api/v1/tasks", body, token);
        assertThat(response.statusCode()).isEqualTo(201);
        return JsonPath.read(response.body(), "$.task_id");
    }

    private HttpResponse<String> uploadFile(String token, String filename, String mimeType, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, "file", filename, mimeType, content);
        writeFieldPart(out, "clientRequestId", UUID.randomUUID().toString());
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/public/worker-links/" + token + "/documents"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .header("Idempotency-Key", "upload-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uploadFileAsType(
            String token,
            String filename,
            String mimeType,
            byte[] content,
            String documentType
    ) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, "file", filename, mimeType, content);
        writeFieldPart(out, "clientRequestId", UUID.randomUUID().toString());
        writeFieldPart(out, "documentType", documentType);
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/public/worker-links/" + token + "/documents"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .header("Idempotency-Key", "upload-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uploadFileWithIdempotencyKey(
            String token,
            String filename,
            String mimeType,
            byte[] content,
            String clientRequestId,
            String idempotencyKey
    ) throws Exception {
        return uploadFileWithIdempotencyKey(
                token,
                filename,
                mimeType,
                content,
                clientRequestId,
                idempotencyKey,
                null
        );
    }

    private HttpResponse<String> uploadFileWithIdempotencyKey(
            String token,
            String filename,
            String mimeType,
            byte[] content,
            String clientRequestId,
            String idempotencyKey,
            String documentType
    ) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, "file", filename, mimeType, content);
        if (clientRequestId != null) {
            writeFieldPart(out, "clientRequestId", clientRequestId);
        }
        if (documentType != null) {
            writeFieldPart(out, "documentType", documentType);
        }
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request = HttpRequest.newBuilder(
                        uri("/api/v1/public/worker-links/" + token + "/documents")
                )
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return httpClient.send(
                request
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private void writePart(ByteArrayOutputStream out, String name, String filename, String mimeType, byte[] content)
            throws IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFieldPart(ByteArrayOutputStream out, String name, String value) throws IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                companyId, name
        );
        jdbcTemplate.update(
                "INSERT INTO company_settings (company_id) VALUES (?)",
                companyId
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
                userId, companyId, email, email, passwordHash, role
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

    private HttpResponse<String> getJson(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJsonWithIdempotencyKey(String path, String body, String token, String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutBody(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patchJson(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> putJson(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
