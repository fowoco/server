package com.fowoco.server.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fowoco.server.aiintegration.application.document.DocumentGenerationClient;
import com.fowoco.server.aiintegration.application.document.GeneratedDocumentFile;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.renewal.RenewalGeneratedDocument;
import com.fowoco.server.aiintegration.application.renewal.RenewalRequestedField;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.file.application.port.FileStorage;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
class RenewalExecutionIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("a8100000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("a8100000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("a8200000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("a8200000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A = UUID.fromString("a8300000-0000-0000-0000-000000000001");
    private static final UUID CASE_A = UUID.fromString("a8400000-0000-0000-0000-000000000001");
    private static final UUID TASK_A = UUID.fromString("a8500000-0000-0000-0000-000000000001");
    private static final UUID TASK_B = UUID.fromString("a8500000-0000-0000-0000-000000000002");
    private static final String PASSWORD = "Test-password-1!";
    private static final String HR_A_EMAIL = "renewal.hr.a@example.com";
    private static final String HR_B_EMAIL = "renewal.hr.b@example.com";
    private static final Pattern REQUEST_ID_LOG_VALUE = Pattern.compile("request_id=([^ ]+)");
    private static final Pattern HTTP_REQUEST_ID_LOG_VALUE = Pattern.compile("http_request_id=([^ ]+)");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private RenewalRuntimeClient runtimeClient;

    @MockitoBean
    private DocumentGenerationClient documentGenerationClient;

    @MockitoBean
    private FileStorage fileStorage;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicReference<RenewalRunRequest> capturedRequest = new AtomicReference<>();
    private final Logger telemetryLogger = (Logger) LoggerFactory.getLogger(
            "com.fowoco.server.task.application.renewal.RenewalExecutionTelemetry"
    );
    private final ListAppender<ILoggingEvent> telemetryAppender = new ListAppender<>();

    @BeforeEach
    void resetAndSeed() {
        telemetryAppender.start();
        telemetryLogger.addAppender(telemetryAppender);
        reset(runtimeClient);
        reset(documentGenerationClient, fileStorage);
        when(documentGenerationClient.generate(any())).thenReturn(new GeneratedDocumentFile(
                "표준근로계약서.hwpx", "hwpx", validHwpxFile()
        ));
        capturedRequest.set(null);
        jdbcTemplate.update("DELETE FROM document_request_draft");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM workflow_case");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company_settings");
        jdbcTemplate.update("DELETE FROM company");
        insertCompany(COMPANY_A, "Renewal 사업장 A");
        insertCompany(COMPANY_B, "Renewal 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash);
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash);
        insertWorker();
        insertCaseAndTask();
        jdbcTemplate.update(
                """
                INSERT INTO worker_document (
                    worker_document_id,worker_id,company_id,document_type,submission_status
                ) VALUES (?,?,?,'PASSPORT_COPY','MISSING')
                """,
                UUID.fromString("a8700000-0000-0000-0000-000000000001"), WORKER_A, COMPANY_A
        );
    }

    @AfterEach
    void detachTelemetryAppender() {
        telemetryLogger.detachAppender(telemetryAppender);
        telemetryAppender.stop();
    }

    @Test
    void sendsServerContextAndRecordsMissingInformationWithoutAutomaticApproval() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation -> {
            RenewalRunRequest request = invocation.getArgument(0);
            capturedRequest.set(request);
            return askHrResponse(request);
        });
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.task_status")).isEqualTo("NEEDS_INFO");
        assertThat(JsonPath.<String>read(response.body(), "$.workflow_id")).isEqualTo("WF-CON-001");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.missing_slots")).containsExactly("wage");
        assertThat(JsonPath.<Boolean>read(response.body(), "$.human_review_required")).isTrue();

        RenewalRunRequest sent = capturedRequest.get();
        assertThat(sent.instruction()).isEqualTo("응웬반안 체류연장 준비해줘");
        assertThat(sent.worker().displayName()).isEqualTo("응웬반안");
        assertThat(sent.company().name()).isEqualTo("Renewal 사업장 A");
        assertThat(sent.documents()).isEmpty();
        assertThat(sent.slots())
                .containsEntry("stay_expiry_date", "2027-08-31")
                .containsEntry("contract_end_date", "2027-08-31");

        String stored = jdbcTemplate.queryForObject(
                "SELECT business_data_json FROM task WHERE task_id = ?", String.class, TASK_A
        );
        assertThat(stored)
                .contains("renewal_execution", "requested_fields", "case_signals")
                .doesNotContain("worker_request_message", "language_assistant");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_request WHERE task_id = ?", Integer.class, TASK_A
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'TASK_UPDATED'",
                Integer.class, TASK_A
        )).isEqualTo(1);
    }

    @Test
    void distinguishesAnInvalidRenewalRequestContract() throws Exception {
        when(runtimeClient.run(any(), any())).thenThrow(new AiRuntimeCallException(
                AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                "invalid test contract"
        ));
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("RENEWAL_REQUEST_CONTRACT_INVALID");
    }

    @Test
    void distinguishesAnUnexpectedAgentWorkflow() throws Exception {
        when(runtimeClient.run(any(), any())).thenThrow(new AiRuntimeCallException(
                AiRuntimeFailureCode.UNEXPECTED_WORKFLOW,
                "unexpected test workflow"
        ));
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("RENEWAL_WORKFLOW_MISMATCH");
    }

    @Test
    void storesAnAgentWorkerMessageAndAllowsApprovalBeforeOcrCollection() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation -> askWorkerResponse(invocation.getArgument(0)));
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.task_status")).isEqualTo("DRAFT");
        assertThat(JsonPath.<String>read(response.body(), "$.scenario")).isEqualTo("ask_worker");
        assertThat(JsonPath.<String>read(response.body(), "$.worker_message_draft_id")).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT language FROM document_request_draft WHERE task_id = ?", String.class, TASK_A
        )).isEqualTo("vi");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM document_request_draft WHERE task_id = ?", String.class, TASK_A
        )).isEqualTo("Vui lòng gửi hộ chiếu cho 담당자.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_link WHERE task_id = ?", Integer.class, TASK_A
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'DOCUMENT_REQUEST_DRAFT_SAVED'",
                Integer.class
        )).isEqualTo(1);

        long taskVersion = ((Number) JsonPath.read(response.body(), "$.task_version")).longValue();
        HttpResponse<String> task = getTask(token);
        assertThat(task.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(task.body(), "$.next_action"))
                .isEqualTo("REQUEST_APPROVAL");
        assertThat(JsonPath.<List<String>>read(task.body(), "$.available_actions"))
                .containsExactly("REQUEST_APPROVAL");

        HttpResponse<String> approval = requestApproval(token, taskVersion);
        assertThat(approval.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(approval.body(), "$.task_status"))
                .isEqualTo("READY_FOR_REVIEW");
    }

    @Test
    void keepsAskWorkerInNeedsInfoWhenAnHrInputIsStillMissing() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation -> {
            RenewalRunRequest request = invocation.getArgument(0);
            return response(
                    request, "ask_worker", "WAITING_WORKER", "WAITING_WORKER",
                    List.of("passport_number", "wage"),
                    List.of(
                            new RenewalRequestedField("passport_number", "DOCUMENT_OCR"),
                            new RenewalRequestedField("wage", "USER_INPUT")
                    ),
                    "여권 사본을 제출해 주세요.",
                    Map.of("target_language", "ko", "translated_text", "여권 사본을 제출해 주세요."),
                    List.of("REQUEST_IDENTITY_DOCUMENT", "NEEDS_INFO")
            );
        });
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.task_status")).isEqualTo("NEEDS_INFO");
        assertThat(JsonPath.<String>read(response.body(), "$.worker_message_draft_id")).isNotBlank();
    }

    @Test
    void keepsAWorkerGuideFailureInHrReviewWithoutCreatingDeliveryResources() throws Exception {
        jdbcTemplate.update("UPDATE task SET status = 'NEEDS_INFO' WHERE task_id = ?", TASK_A);
        when(runtimeClient.run(any(), any())).thenAnswer(invocation ->
                workerGuideReviewResponse(invocation.getArgument(0))
        );
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.task_status")).isEqualTo("DRAFT");
        assertThat(JsonPath.<Boolean>read(response.body(), "$.guide_review_required")).isTrue();
        assertThat(JsonPath.<String>read(response.body(), "$.guide_failure_code"))
                .isEqualTo("LANGUAGE_ASSISTANT_REVIEW_REQUIRED");
        assertThat(JsonPath.<String>read(
                response.body(), "$.guide_review_draft.target_language"
        )).isEqualTo("vi");
        assertThat(JsonPath.<String>read(
                response.body(), "$.guide_review_draft.generation_status"
        )).isEqualTo("warning");
        assertThat(JsonPath.<String>read(
                response.body(), "$.guide_review_draft.standard_korean_text"
        )).isEqualTo("여권 사본을 제출해 주세요.");
        assertThat(JsonPath.<String>read(
                response.body(), "$.guide_review_draft.translated_text"
        )).isEqualTo("Vui lòng nộp bản sao hộ chiếu.");
        assertThat(JsonPath.<String>read(
                response.body(), "$.guide_review_draft.warning_codes[0]"
        )).isEqualTo("SEMANTIC_VALIDATION_INCONCLUSIVE");
        assertThat(JsonPath.<Object>read(response.body(), "$.worker_message_draft_id")).isNull();

        String businessData = jdbcTemplate.queryForObject(
                "SELECT business_data_json FROM task WHERE task_id = ?", String.class, TASK_A
        );
        assertThat(JsonPath.<Boolean>read(
                businessData,
                "$.renewal_execution.guide_review_required"
        )).isTrue();
        assertThat(JsonPath.<String>read(
                businessData,
                "$.renewal_execution.guide_failure_code"
        )).isEqualTo("LANGUAGE_ASSISTANT_REVIEW_REQUIRED");
        assertThat(JsonPath.<String>read(
                businessData,
                "$.renewal_execution.guide_review_draft.easy_korean_text"
        )).isEqualTo("여권을 내 주세요.");
        assertThat(businessData).doesNotContain("provider_raw_secret", "do-not-persist");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_request_draft WHERE task_id = ?",
                Integer.class,
                TASK_A
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_link WHERE task_id = ?",
                Integer.class,
                TASK_A
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT change_summary FROM audit_event WHERE target_id = ? AND action = 'TASK_UPDATED'",
                String.class,
                TASK_A
        )).contains("LANGUAGE_ASSISTANT_REVIEW_REQUIRED");
    }

    @Test
    void storesHrSlotAnswersAndSendsThemToTheNextRenewalRun() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation -> {
            RenewalRunRequest request = invocation.getArgument(0);
            capturedRequest.set(request);
            return request.slots().containsKey("wage")
                    ? generateResponse(request)
                    : askHrResponse(request);
        });
        String token = login(HR_A_EMAIL);

        HttpResponse<String> first = postRenewal(token, 0);
        long nextVersion = ((Number) JsonPath.read(first.body(), "$.task_version")).longValue();
        HttpResponse<String> second = postRenewalWithSlots(
                token,
                nextVersion,
                "{\"wage\":\"2,500,000\"}"
        );

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(capturedRequest.get().slots()).containsEntry("wage", "2500000");
        String businessData = jdbcTemplate.queryForObject(
                "SELECT business_data_json FROM task WHERE task_id = ?", String.class, TASK_A
        );
        assertThat(JsonPath.<String>read(businessData, "$.renewal_inputs.wage"))
                .isEqualTo("2500000");
        verify(runtimeClient, times(2)).run(any(), any());
    }

    @Test
    void reusesSafeContractInputsFromThePreviousTaskInTheSameCase() throws Exception {
        jdbcTemplate.update(
                """
                UPDATE task
                SET business_data_json = ?, status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE task_id = ?
                """,
                """
                {
                  "renewal_inputs": {
                    "wage": "2500000",
                    "working_hours": "40",
                    "job_description": "생산 업무",
                    "work_location": "제1공장",
                    "lodging": "회사 기숙사",
                    "contract_period": "2026-09-01~2027-08-31",
                    "passport_number": "must-not-be-inherited"
                  }
                }
                """,
                TASK_A
        );
        insertSecondCaseTask();
        when(runtimeClient.run(any(), any())).thenAnswer(invocation -> {
            RenewalRunRequest request = invocation.getArgument(0);
            capturedRequest.set(request);
            return generateResponse(request);
        });
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, TASK_B, 0);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(capturedRequest.get().slots())
                .containsEntry("wage", "2500000")
                .containsEntry("working_hours", "40")
                .containsEntry("job_description", "생산 업무")
                .containsEntry("work_location", "제1공장")
                .containsEntry("lodging", "회사 기숙사")
                .containsEntry("contract_period", "2026-09-01~2027-08-31")
                .containsEntry("due_at", "2026-08-21")
                .doesNotContainKey("passport_number");
    }

    @Test
    void rejectsDocumentOcrSlotsSubmittedByTheClient() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation ->
                askWorkerResponse(invocation.getArgument(0))
        );
        String token = login(HR_A_EMAIL);

        HttpResponse<String> first = postRenewal(token, 0);
        long nextVersion = ((Number) JsonPath.read(first.body(), "$.task_version")).longValue();
        HttpResponse<String> second = postRenewalWithSlots(
                token,
                nextVersion,
                "{\"passport_number\":\"M12345678\"}"
        );

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(second.body(), "$.code"))
                .isEqualTo("INVALID_RENEWAL_SLOT_ANSWER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT business_data_json FROM task WHERE task_id = ?", String.class, TASK_A
        )).doesNotContain("M12345678", "renewal_inputs");
        verify(runtimeClient, times(1)).run(any(), any());
    }

    @Test
    void rejectsAnInvalidValueForARequestedHrSlot() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation ->
                askHrResponse(invocation.getArgument(0))
        );
        String token = login(HR_A_EMAIL);

        HttpResponse<String> first = postRenewal(token, 0);
        long nextVersion = ((Number) JsonPath.read(first.body(), "$.task_version")).longValue();
        HttpResponse<String> second = postRenewalWithSlots(
                token,
                nextVersion,
                "{\"wage\":null}"
        );

        assertThat(second.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(second.body(), "$.code"))
                .isEqualTo("INVALID_RENEWAL_SLOT_ANSWER");
        verify(runtimeClient, times(1)).run(any(), any());
    }

    @Test
    void hidesAnotherCompanyTaskAndDoesNotCallTheAgent() throws Exception {
        String token = login(HR_B_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(404);
        verifyNoInteractions(runtimeClient);
    }

    @Test
    void replacesTheExistingReviewRequestWhenCriticalAgentDataChanges() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation ->
                generateResponse(invocation.getArgument(0))
        );
        String token = login(HR_A_EMAIL);
        HttpResponse<String> review = requestApproval(token);
        assertThat(review.statusCode()).isEqualTo(201);
        long reviewTaskVersion = ((Number) JsonPath.read(review.body(), "$.task_version")).longValue();

        HttpResponse<String> response = postRenewal(token, reviewTaskVersion);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.task_status"))
                .isEqualTo("READY_FOR_REVIEW");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_request WHERE task_id = ? AND status = 'INVALIDATED'",
                Integer.class,
                TASK_A
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_request WHERE task_id = ? AND status = 'PENDING'",
                Integer.class,
                TASK_A
        )).isEqualTo(1);
    }

    @Test
    void generatesAndLinksADraftWithoutPersistingTheRawValues() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation ->
                generateResponse(invocation.getArgument(0))
        );
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.generated_documents[0].template_id"))
                .isEqualTo("standard_labor_contract_v6");
        assertThat(JsonPath.<String>read(response.body(), "$.generated_documents[0].status"))
                .isEqualTo("GENERATED");
        assertThat(JsonPath.<String>read(response.body(), "$.generated_documents[0].format"))
                .isEqualTo("hwpx");
        assertThat(JsonPath.<String>read(response.body(), "$.generated_documents[0].stored_file_id"))
                .isNotBlank();
        assertThat(JsonPath.<String>read(response.body(), "$.generated_documents[0].worker_document_id"))
                .isNotBlank();
        assertThat(response.body()).doesNotContain("NGUYEN VAN AN", "passport_number");

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM stored_file
                WHERE task_id = ? AND worker_id = ?
                    AND mime_type = 'application/vnd.hancom.hwpx'
                    AND LOWER(name) LIKE '%.hwpx'
                """,
                Integer.class, TASK_A, WORKER_A
        )).isEqualTo(1);
        verify(documentGenerationClient).generate(argThat(request ->
                "hwpx".equals(request.format())
                        && "standard_labor_contract_v6".equals(request.templateId())
        ));
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM worker_document
                WHERE task_id = ? AND worker_id = ? AND file_id IS NOT NULL
                    AND document_type = 'CONTRACT' AND submission_status = 'SUBMITTED'
                    AND source = 'AI_GENERATED'
                """,
                Integer.class, TASK_A, WORKER_A
        )).isEqualTo(1);
        String businessData = jdbcTemplate.queryForObject(
                "SELECT business_data_json FROM task WHERE task_id = ?", String.class, TASK_A
        );
        assertThat(businessData)
                .contains("stored_file_id", "worker_document_id")
                .doesNotContain("NGUYEN VAN AN", "passport_number", "values");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE change_summary LIKE '%NGUYEN VAN AN%'",
                Integer.class
        )).isZero();

        HttpResponse<String> retry = postRenewal(token, 0);
        assertThat(retry.statusCode()).isEqualTo(409);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE task_id = ?",
                Integer.class, TASK_A
        )).isEqualTo(1);
    }

    @Test
    void recordsSafeServerStageDurationsForGeneratedDocumentFlow() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation -> {
            RenewalRunRequest request = invocation.getArgument(0);
            capturedRequest.set(request);
            return generateResponse(request);
        });
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> logs = telemetryAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(logs)
                .hasSize(5)
                .anyMatch(value -> value.contains("stage=CONTEXT_LOAD status=SUCCESS"))
                .anyMatch(value -> value.contains("stage=RENEWAL_RUNTIME_CALL status=SUCCESS"))
                .anyMatch(value -> value.contains("stage=DOCUMENT_GENERATION status=SUCCESS"))
                .anyMatch(value -> value.contains("stage=RESULT_APPLY status=SUCCESS"))
                .anyMatch(value -> value.contains("stage=TOTAL status=SUCCESS"));
        assertThat(logs).allMatch(value ->
                value.matches(".*duration_ms=\\d+(?: error_code=[A-Z_]+)?$")
        );
        assertThat(logs.stream().map(this::requestIdFromLog).distinct())
                .containsExactly(capturedRequest.get().requestId().toString());
        assertThat(logs.stream().map(this::httpRequestIdFromLog).distinct()).hasSize(1);
        assertThat(String.join("\n", logs))
                .doesNotContain(
                        "응웬반안",
                        "NGUYEN VAN AN",
                        "M12345678",
                        "passport_number"
                );
    }

    @Test
    void keepsTheTaskInNeedsInfoWhenARequiredChecklistItemIsIncomplete() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO task_checklist_item (
                    checklist_item_id,task_id,company_id,item_code,label,required,completed,
                    created_at,updated_at,version
                ) VALUES (?,?,?,'SIGNED_CONTRACT','서명 계약서 확인',TRUE,FALSE,
                    CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """,
                UUID.fromString("a8600000-0000-0000-0000-000000000001"), TASK_A, COMPANY_A
        );
        when(runtimeClient.run(any(), any())).thenAnswer(invocation ->
                generateResponse(invocation.getArgument(0))
        );
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.task_status"))
                .isEqualTo("NEEDS_INFO");
    }

    @Test
    void recordsOutOfScopeWithoutAutomaticallyCancellingTheTask() throws Exception {
        when(runtimeClient.run(any(), any())).thenAnswer(invocation ->
                outOfScopeResponse(invocation.getArgument(0))
        );
        String token = login(HR_A_EMAIL);

        HttpResponse<String> response = postRenewal(token, 0);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.intent")).isEqualTo("OUT_OF_SCOPE");
        assertThat(JsonPath.<String>read(response.body(), "$.task_status")).isEqualTo("NEEDS_INFO");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE task_id = ?", String.class, TASK_A
        )).isEqualTo("NEEDS_INFO");
    }

    private RenewalRunResponse askHrResponse(RenewalRunRequest request) {
        return response(
                request, "ask_hr", "NEEDS_INFO", "NEEDS_INFO", List.of("wage"),
                List.of(new RenewalRequestedField("wage", "USER_INPUT")), null, null,
                List.of("REQUEST_CONTRACT_SLOTS", "NEEDS_INFO")
        );
    }

    private String requestIdFromLog(String value) {
        Matcher matcher = REQUEST_ID_LOG_VALUE.matcher(value);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String httpRequestIdFromLog(String value) {
        Matcher matcher = HTTP_REQUEST_ID_LOG_VALUE.matcher(value);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private RenewalRunResponse askWorkerResponse(RenewalRunRequest request) {
        return response(
                request, "ask_worker", "WAITING_WORKER", "WAITING_WORKER",
                List.of("passport_number"),
                List.of(new RenewalRequestedField("passport_number", "DOCUMENT_OCR")),
                "Vui lòng gửi hộ chiếu cho 담당자.",
                Map.of("target_language", "vi", "translated_text", "Vui lòng gửi hộ chiếu cho 담당자."),
                List.of("REQUEST_IDENTITY_DOCUMENT", "NEEDS_INFO")
        );
    }

    private RenewalRunResponse generateResponse(RenewalRunRequest request) {
        List<RenewalGeneratedDocument> generatedDocuments = switch (request.task().taskType()) {
            case "RECONTRACT" -> List.of(generatedDocument("standard_labor_contract_v6"));
            case "EMPLOYMENT_PERIOD_EXTENSION" ->
                    List.of(generatedDocument("employment_extension_application_v12_3"));
            case "STAY_PERIOD_EXTENSION" -> List.of(
                    generatedDocument("immigration_integrated_application_v34"),
                    generatedDocument("identity_guaranty_v129")
            );
            default -> throw new IllegalArgumentException("unsupported test task type");
        };
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                request.task().workflowId(), new BigDecimal("0.94"), "READY_FOR_REVIEW", "REVIEW_REQUIRED",
                "generate", "PHASE_4", "STEP_13", Map.of(), List.of(), List.of(),
                null, null, false, null, null, null,
                generatedDocuments,
                List.of(), null, List.of("GENERATE_DRAFTS", "READY_FOR_REVIEW"),
                List.of(), null, "rules", "main", List.of()
        );
    }

    private RenewalGeneratedDocument generatedDocument(String templateId) {
        return new RenewalGeneratedDocument(
                templateId,
                "Renewal test document",
                "hwpx",
                "stub",
                null,
                null,
                List.copyOf(generatedValues(templateId).keySet()),
                List.of(),
                generatedValues(templateId)
        );
    }

    private Map<String, Object> generatedValues(String templateId) {
        return switch (templateId) {
            case "standard_labor_contract_v6" -> Map.of(
                    "employee_name", "NGUYEN VAN AN",
                    "employee_birthdate", "1995-04-12",
                    "enterprise_name", "Renewal 사업장 A"
            );
            case "employment_extension_application_v12_3" -> Map.of(
                    "employee_1_name", "NGUYEN VAN AN",
                    "employee_1_resident_number", "950412-5123456",
                    "employee_1_passport_number", "M12345678"
            );
            case "immigration_integrated_application_v34" -> Map.of(
                    "given_names", "VAN AN",
                    "passport_number", "M12345678",
                    "birth_year", "1995",
                    "birth_month", "04",
                    "birth_day", "12"
            );
            case "identity_guaranty_v129" -> Map.of(
                    "foreign_name", "NGUYEN VAN AN",
                    "foreign_birthdate", "1995-04-12",
                    "foreign_passport", "M12345678"
            );
            default -> throw new IllegalArgumentException("unsupported test template");
        };
    }

    private RenewalRunResponse workerGuideReviewResponse(RenewalRunRequest request) {
        Map<String, Object> languageAssistant = new java.util.LinkedHashMap<>();
        languageAssistant.put("target_language", "vi");
        languageAssistant.put("generation_status", "warning");
        languageAssistant.put("requires_human_review", true);
        languageAssistant.put("standard_korean_text", "여권 사본을 제출해 주세요.");
        languageAssistant.put("easy_korean_text", "여권을 내 주세요.");
        languageAssistant.put("translated_text", "Vui lòng nộp bản sao hộ chiếu.");
        languageAssistant.put("warnings", List.of(Map.of(
                "component", "translation",
                "code", "SEMANTIC_VALIDATION_INCONCLUSIVE",
                "message", "번역 의미 검토가 필요합니다."
        )));
        languageAssistant.put("provider_raw_secret", "do-not-persist");
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                request.task().workflowId(), new BigDecimal("0.91"),
                "READY_FOR_REVIEW", "REVIEW_REQUIRED", "ask_worker",
                "PHASE_3", "STEP_5", Map.of(), List.of("passport_number"),
                List.of(new RenewalRequestedField("passport_number", "DOCUMENT_OCR")),
                null, null, true, "LANGUAGE_ASSISTANT_REVIEW_REQUIRED", languageAssistant, null,
                List.of(), List.of(), null, List.of("REVIEW_WORKER_GUIDE"),
                List.of(), null, "rules", "main", List.of()
        );
    }

    private byte[] validHwpxFile() {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("mimetype"));
            zip.write("application/hwp+zip".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("Contents/section0.xml"));
            zip.write("<xml>Renewal draft</xml>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private RenewalRunResponse outOfScopeResponse(RenewalRunRequest request) {
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "OUT_OF_SCOPE", "",
                new BigDecimal("0.93"), "CANCELLED", "OUT_OF_SCOPE", "out_of_scope",
                "PHASE_1", "STEP_2", Map.of(), List.of(), List.of(),
                "지원 범위를 벗어난 요청입니다.", null, false, null, null, null, List.of(), List.of(),
                null, List.of("CANCEL_OUT_OF_SCOPE"), List.of(), null, "rules", "main",
                List.of()
        );
    }

    private RenewalRunResponse response(
            RenewalRunRequest request,
            String scenario,
            String status,
            String outcome,
            List<String> missingSlots,
            List<RenewalRequestedField> requestedFields,
            String workerMessage,
            Map<String, Object> languageAssistant,
            List<String> signals
    ) {
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                request.task().workflowId(), new BigDecimal("0.91"), status, outcome, scenario,
                "PHASE_2", "STEP_5", Map.of(), missingSlots, requestedFields,
                null, workerMessage, false, null, languageAssistant, null, List.of(), List.of(), null,
                signals, List.of(), null, "rules", "main", List.of()
        );
    }

    private HttpResponse<String> postRenewal(String token, long version) throws Exception {
        return postRenewal(token, TASK_A, version);
    }

    private HttpResponse<String> postRenewal(String token, UUID taskId, long version) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/tasks/" + taskId + "/renewal-run"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"instruction":"응웬반안 체류연장 준비해줘","expected_version":%d}
                        """.formatted(version)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postRenewalWithSlots(
            String token,
            long version,
            String slotAnswers
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/tasks/" + TASK_A + "/renewal-run"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "instruction":"응웬반안 체류연장 준비해줘",
                          "expected_version":%d,
                          "slot_answers":%s
                        }
                        """.formatted(version, slotAnswers)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> requestApproval(String token) throws Exception {
        return requestApproval(token, 0);
    }

    private HttpResponse<String> requestApproval(String token, long expectedVersion) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        uri("/api/v1/tasks/" + TASK_A + "/approval-requests")
                )
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "expected_version":%d,
                          "ai_snapshot":{"intent":"EXPIRY_RENEWAL"},
                          "hr_snapshot":{"worker_id":"%s"},
                          "changed_fields":[],
                          "source_versions":{"workflow_catalog_version":"0.2.0"}
                        }
                        """.formatted(expectedVersion, WORKER_A)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTask(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/tasks/" + TASK_A))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String login(String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, PASSWORD)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void insertCompany(UUID id, String name) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id,name,status,created_at,updated_at,version) "
                        + "VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", id, name
        );
        jdbcTemplate.update("INSERT INTO company_settings (company_id) VALUES (?)", id);
    }

    private void insertUser(UUID id, UUID companyId, String email, String hash) {
        jdbcTemplate.update(
                "INSERT INTO user_account (user_id,company_id,email,normalized_email,password_hash,role,status,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,'HR','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                id, companyId, email, email, hash
        );
    }

    private void insertWorker() {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id,company_id,display_name,nationality_code,preferred_language,
                    work_status,visa_type,stay_expiry_date,contract_start_date,contract_end_date,
                    created_at,updated_at,version
                ) VALUES (?,?,?,?,?,'ACTIVE','E-9',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """,
                WORKER_A, COMPANY_A, "응웬반안", "VN", "vi",
                LocalDate.of(2027, 8, 31), LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31)
        );
    }

    private void insertCaseAndTask() {
        jdbcTemplate.update(
                """
                INSERT INTO workflow_case (
                    case_id,company_id,worker_id,title,lifecycle_status,priority,
                    workflow_catalog_version,workflow_snapshot_json,created_by,created_at,updated_at,version
                ) VALUES (?,?,?,'3년 만료 재계약·연장','ACTIVE','NORMAL','0.2.0','{}',?,
                    CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """,
                CASE_A, COMPANY_A, WORKER_A, HR_A
        );
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id,company_id,target_type,worker_id,case_id,task_type,workflow_id,
                    workflow_catalog_version,title,description,business_data_json,critical_fingerprint,
                    content_revision,source,status,due_date,created_by,updated_by,created_at,updated_at,version
                ) VALUES (?,?, 'WORKER',?,?, 'RECONTRACT','WF-CON-001','0.2.0',
                    '재계약 조건 확인','근로조건을 확인합니다.','{}',?,0,'AI_CANDIDATE','DRAFT',?,
                    ?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """,
                TASK_A, COMPANY_A, WORKER_A, CASE_A, "0".repeat(64), LocalDate.of(2026, 8, 20), HR_A, HR_A
        );
    }

    private void insertSecondCaseTask() {
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id,company_id,target_type,worker_id,case_id,task_type,workflow_id,
                    workflow_catalog_version,title,description,business_data_json,critical_fingerprint,
                    content_revision,source,status,due_date,created_by,updated_by,created_at,updated_at,version
                ) VALUES (?,?, 'WORKER',?,?, 'STAY_PERIOD_EXTENSION','WF-STY-001','0.2.0',
                    '체류기간 연장 준비','체류기간 연장을 준비합니다.','{}',?,0,'AI_CANDIDATE','DRAFT',?,
                    ?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                """,
                TASK_B, COMPANY_A, WORKER_A, CASE_A, "1".repeat(64),
                LocalDate.of(2026, 8, 21), HR_A, HR_A
        );
    }
}
