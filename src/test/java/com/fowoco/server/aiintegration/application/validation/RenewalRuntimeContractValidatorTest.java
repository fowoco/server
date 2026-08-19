package com.fowoco.server.aiintegration.application.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.renewal.RenewalCompanySnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalGeneratedDocument;
import com.fowoco.server.aiintegration.application.renewal.RenewalRequestedField;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.aiintegration.application.renewal.RenewalTaskSnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalWorkerSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenewalRuntimeContractValidatorTest {

    private final RenewalRuntimeContractValidator validator =
            new RenewalRuntimeContractValidator(new AiRuntimeBoundaryPolicy());

    @Test
    void acceptsTheAgentRenewalContract() {
        RenewalRunRequest request = request();

        assertThatCode(() -> validator.validateResponse(request, response(request)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsStayAndEmploymentExtensionWorkflowPairs() {
        RenewalRunRequest stayRequest = request("STAY_PERIOD_EXTENSION", "WF-STY-001");
        RenewalRunRequest employmentRequest = request(
                "EMPLOYMENT_PERIOD_EXTENSION",
                "WF-CON-001"
        );

        assertThatCode(() -> validator.validateResponse(stayRequest, response(stayRequest)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateResponse(
                employmentRequest,
                response(employmentRequest)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsATaskTypeAndWorkflowMismatch() {
        RenewalRunRequest request = request("STAY_PERIOD_EXTENSION", "WF-CON-001");

        assertThatThrownBy(() -> validator.validateRequest(request))
                .isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAResponseThatChangesTheTaskWorkflow() {
        RenewalRunRequest request = request();

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                response(request, "WF-STY-001")
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAnIntentUsedAsAWorkflowId() {
        RenewalRunRequest request = request();
        RenewalRunResponse valid = response(request);
        RenewalRunResponse invalid = new RenewalRunResponse(
                valid.requestId(), valid.attemptId(), valid.taskId(), valid.intent(),
                "EXPIRY_RENEWAL", valid.confidence(), valid.status(), valid.outcome(),
                valid.scenario(), valid.phase(), valid.step(), valid.slots(), valid.missingSlots(),
                valid.requestedFields(), valid.guideMessage(), valid.workerRequestMessage(),
                valid.guideReviewRequired(), valid.guideFailureCode(),
                valid.languageAssistant(), valid.ocrResult(), valid.generatedDocuments(), valid.evidence(),
                valid.documentValidation(), valid.caseSignals(), valid.progressEvents(),
                valid.supervisorReason(), valid.supervisorSource(), valid.activeSubgraph(), valid.errors()
        );

        assertThatThrownBy(() -> validator.validateResponse(request, invalid))
                .isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsLegacyHwpGeneratedDocuments() {
        RenewalRunRequest request = request();

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                generateResponse(
                        request,
                        Map.of("employee_name", "NGUYEN VAN AN"),
                        "hwp"
                )
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAResponseFromAnotherAttempt() {
        RenewalRunRequest request = request();
        RenewalRunResponse valid = response(request);
        RenewalRunResponse staleResponse = new RenewalRunResponse(
                valid.requestId(), UUID.randomUUID(), valid.taskId(), valid.intent(),
                valid.workflowId(), valid.confidence(), valid.status(), valid.outcome(),
                valid.scenario(), valid.phase(), valid.step(), valid.slots(), valid.missingSlots(),
                valid.requestedFields(), valid.guideMessage(), valid.workerRequestMessage(),
                valid.guideReviewRequired(), valid.guideFailureCode(),
                valid.languageAssistant(), valid.ocrResult(), valid.generatedDocuments(), valid.evidence(),
                valid.documentValidation(), valid.caseSignals(), valid.progressEvents(),
                valid.supervisorReason(), valid.supervisorSource(), valid.activeSubgraph(), valid.errors()
        );

        assertThatThrownBy(() -> validator.validateResponse(request, staleResponse))
                .isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void acceptsAgentIdentitySignalsAndNullableLanguageFields() {
        RenewalRunRequest request = request();
        RenewalRunResponse valid = response(request);
        Map<String, Object> language = new LinkedHashMap<>();
        language.put("target_language", "vi");
        language.put("standard_korean_text", "여권을 제출해 주세요.");
        language.put("translated_text", null);
        RenewalRunResponse response = new RenewalRunResponse(
                valid.requestId(), valid.attemptId(), valid.taskId(), valid.intent(),
                valid.workflowId(), valid.confidence(), "WAITING_WORKER", "WAITING_WORKER",
                "ask_worker", valid.phase(), valid.step(), valid.slots(),
                List.of("passport_number", "alien_registration_number"),
                List.of(
                        new RenewalRequestedField("passport_number", "DOCUMENT_OCR"),
                        new RenewalRequestedField("alien_registration_number", "DOCUMENT_OCR")
                ),
                valid.guideMessage(), "여권과 외국인등록증을 제출해 주세요.", false, null, language,
                valid.ocrResult(), valid.generatedDocuments(), valid.evidence(),
                valid.documentValidation(),
                List.of(
                        "REQUEST_IDENTITY_DOCUMENT",
                        "REQUEST_PASSPORT",
                        "REQUEST_ALIEN_REGISTRATION"
                ),
                valid.progressEvents(), valid.supervisorReason(), valid.supervisorSource(),
                valid.activeSubgraph(), valid.errors()
        );

        assertThatCode(() -> validator.validateResponse(request, response))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnAutomaticWorkerGuideWithoutALanguageAssistantResult() {
        RenewalRunRequest request = request();
        RenewalRunResponse invalid = automaticWorkerGuide(
                request,
                "여권과 외국인등록증을 제출해 주세요.",
                null
        );

        assertThatThrownBy(() -> validator.validateResponse(request, invalid))
                .isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAnAutomaticWorkerGuideForAnotherLanguage() {
        RenewalRunRequest request = request();
        Map<String, Object> language = successfulLanguageAssistant("ko");

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                automaticWorkerGuide(request, "여권과 외국인등록증을 제출해 주세요.", language)
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAnAutomaticWorkerGuideThatExposesInternalKeys() {
        RenewalRunRequest request = request();
        Map<String, Object> language = successfulLanguageAssistant("vi");

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                automaticWorkerGuide(
                        request,
                        "필요한 정보: alien_registration_number (조합:both_missing)",
                        language
                )
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void acceptsAllWorkerGuideFailureCodesAsFailClosedReviewResponses() {
        RenewalRunRequest request = request();

        for (String failureCode : List.of(
                "LANGUAGE_ASSISTANT_NOT_CONFIGURED",
                "LANGUAGE_ASSISTANT_INVOCATION_FAILED",
                "LANGUAGE_ASSISTANT_REVIEW_REQUIRED",
                "WORKER_GUIDE_UNAVAILABLE"
        )) {
            assertThatCode(() -> validator.validateResponse(
                    request,
                    workerGuideReviewResponse(request, failureCode, null, true)
            )).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsMalformedLanguageAssistantWarnings() {
        RenewalRunRequest request = request();
        RenewalRunResponse valid = response(request);
        Map<String, Object> language = new LinkedHashMap<>();
        language.put("target_language", "vi");
        language.put("generation_status", "warning");
        language.put("warnings", List.of(Map.of("message", "code가 없습니다.")));
        RenewalRunResponse invalid = new RenewalRunResponse(
                valid.requestId(), valid.attemptId(), valid.taskId(), valid.intent(),
                valid.workflowId(), valid.confidence(), valid.status(), valid.outcome(),
                valid.scenario(), valid.phase(), valid.step(), valid.slots(), valid.missingSlots(),
                valid.requestedFields(), valid.guideMessage(), valid.workerRequestMessage(),
                valid.guideReviewRequired(), valid.guideFailureCode(), language,
                valid.ocrResult(), valid.generatedDocuments(), valid.evidence(),
                valid.documentValidation(), valid.caseSignals(), valid.progressEvents(),
                valid.supervisorReason(), valid.supervisorSource(), valid.activeSubgraph(), valid.errors()
        );

        assertThatThrownBy(() -> validator.validateResponse(request, invalid))
                .isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsNonTextLanguageAssistantContent() {
        RenewalRunRequest request = request();
        Map<String, Object> language = reviewLanguageAssistant();
        language.put("translated_text", Map.of("raw", "검증되지 않은 Provider 응답"));

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                workerGuideReviewResponse(
                        request,
                        "LANGUAGE_ASSISTANT_REVIEW_REQUIRED",
                        null,
                        true,
                        language
                )
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsInconsistentLanguageAssistantReviewState() {
        RenewalRunRequest request = request();
        Map<String, Object> language = reviewLanguageAssistant();
        language.put("generation_status", "success");
        language.put("requires_human_review", false);

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                workerGuideReviewResponse(
                        request,
                        "LANGUAGE_ASSISTANT_REVIEW_REQUIRED",
                        null,
                        true,
                        language
                )
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAWorkerGuideReviewResponseThatContainsAnAutomaticDeliveryMessage() {
        RenewalRunRequest request = request();

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                workerGuideReviewResponse(
                        request,
                        "LANGUAGE_ASSISTANT_REVIEW_REQUIRED",
                        "검토하지 않은 안내입니다.",
                        true
                )
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAWorkerGuideReviewSignalWithoutTheReviewFlag() {
        RenewalRunRequest request = request();

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                workerGuideReviewResponse(
                        request,
                        "LANGUAGE_ASSISTANT_NOT_CONFIGURED",
                        null,
                        false
                )
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void rejectsAnUnknownWorkerGuideFailureCode() {
        RenewalRunRequest request = request();

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                workerGuideReviewResponse(request, "RAW_PROVIDER_ERROR", null, true)
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    @Test
    void acceptsTheAgentOutOfScopeResultWithoutTreatingItAsRenewal() {
        RenewalRunRequest request = request();
        RenewalRunResponse valid = response(request);
        RenewalRunResponse outOfScope = new RenewalRunResponse(
                valid.requestId(), valid.attemptId(), valid.taskId(), "OUT_OF_SCOPE", "",
                new BigDecimal("0.93"), "CANCELLED", "OUT_OF_SCOPE", "out_of_scope",
                "PHASE_1", "STEP_2", Map.of(), List.of(), List.of(),
                "지원 범위를 벗어난 요청입니다.", null, false, null, null, null, List.of(), List.of(),
                null, List.of("CANCEL_OUT_OF_SCOPE"), List.of(), null, "rules", "main",
                List.of()
        );

        assertThatCode(() -> validator.validateResponse(request, outOfScope))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsGeneratedDocumentValuesForTheCanonicalTemplate() {
        RenewalRunRequest request = request();

        assertThatCode(() -> validator.validateResponse(
                request,
                generateResponse(request, Map.of("employee_name", "NGUYEN VAN AN"))
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsAGeneratedDocumentWithoutValues() {
        RenewalRunRequest request = request();

        assertThatThrownBy(() -> validator.validateResponse(
                request,
                generateResponse(request, Map.of())
        )).isInstanceOf(AiRuntimeContractException.class);
    }

    private RenewalRunRequest request() {
        return request("RECONTRACT", "WF-CON-001");
    }

    private RenewalRunRequest request(String taskType, String workflowId) {
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        return new RenewalRunRequest(
                requestId, attemptId, "응웬반안 체류연장 준비해줘", workerId, companyId, taskId,
                Map.of("worker_id", workerId.toString()), List.of(), null,
                new RenewalWorkerSnapshot(
                        workerId, companyId, "응웬반안", "VN", "vi", "ACTIVE", "E-9",
                        null, null, null, null, null, now, now, 0
                ),
                new RenewalCompanySnapshot(companyId, "테스트 사업장", "ACTIVE", now, now, 0),
                new RenewalTaskSnapshot(
                        taskId, companyId, workerId, UUID.randomUUID(), taskType, workflowId,
                        "0.2.0", "재계약", null, Map.of(), 0, "AI_ANALYZED", "DRAFT", null,
                        UUID.randomUUID(), UUID.randomUUID(), now, now, 0
                )
        );
    }

    private RenewalRunResponse response(RenewalRunRequest request) {
        return response(request, request.task().workflowId());
    }

    private RenewalRunResponse response(RenewalRunRequest request, String workflowId) {
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                workflowId, new BigDecimal("0.91"), "NEEDS_INFO", "NEEDS_INFO", "ask_hr",
                "PHASE_2", "STEP_5", Map.of(), List.of("wage"),
                List.of(new RenewalRequestedField("wage", "USER_INPUT")),
                "임금을 확인해 주세요.", null, false, null, null, null, List.of(), List.of(), null,
                List.of("REQUEST_CONTRACT_SLOTS", "NEEDS_INFO"), List.of(), null, "rules", "main",
                List.of()
        );
    }

    private RenewalRunResponse generateResponse(
            RenewalRunRequest request,
            Map<String, Object> values
    ) {
        return generateResponse(request, values, "hwpx");
    }

    private RenewalRunResponse generateResponse(
            RenewalRunRequest request,
            Map<String, Object> values,
            String format
    ) {
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                request.task().workflowId(), new BigDecimal("0.94"),
                "READY_FOR_REVIEW", "REVIEW_REQUIRED",
                "generate", "PHASE_4", "STEP_13", Map.of(), List.of(), List.of(),
                null, null, false, null, null, null,
                List.of(new RenewalGeneratedDocument(
                        "standard_labor_contract_v6", "표준근로계약서", format, "stub", null, null,
                        List.copyOf(values.keySet()), List.of(), values
                )),
                List.of(), null, List.of("GENERATE_DRAFTS", "READY_FOR_REVIEW"),
                List.of(), null, "rules", "main", List.of()
        );
    }

    private RenewalRunResponse workerGuideReviewResponse(
            RenewalRunRequest request,
            String failureCode,
            String workerMessage,
            boolean reviewRequired
    ) {
        return workerGuideReviewResponse(
                request, failureCode, workerMessage, reviewRequired, null
        );
    }

    private RenewalRunResponse automaticWorkerGuide(
            RenewalRunRequest request,
            String workerMessage,
            Map<String, Object> languageAssistant
    ) {
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                request.task().workflowId(), new BigDecimal("0.91"),
                "WAITING_WORKER", "WAITING_WORKER", "ask_worker",
                "PHASE_3", "STEP_5", Map.of(),
                List.of("passport_number", "alien_registration_number"),
                List.of(
                        new RenewalRequestedField("passport_number", "DOCUMENT_OCR"),
                        new RenewalRequestedField("alien_registration_number", "DOCUMENT_OCR")
                ),
                null, workerMessage, false, null, languageAssistant, null,
                List.of(), List.of(), Map.of("combo", "both_missing"),
                List.of("REQUEST_IDENTITY_DOCUMENT"), List.of(), null, "rules", "main",
                List.of()
        );
    }

    private Map<String, Object> successfulLanguageAssistant(String targetLanguage) {
        Map<String, Object> language = new LinkedHashMap<>();
        language.put("target_language", targetLanguage);
        language.put("generation_status", "success");
        language.put("requires_human_review", false);
        language.put("standard_korean_text", "여권과 외국인등록증을 제출해 주세요.");
        language.put("easy_korean_text", "여권과 외국인등록증을 내 주세요.");
        language.put("translated_text", "Vui lòng nộp hộ chiếu và thẻ đăng ký người nước ngoài.");
        language.put("warnings", List.of());
        return language;
    }

    private RenewalRunResponse workerGuideReviewResponse(
            RenewalRunRequest request,
            String failureCode,
            String workerMessage,
            boolean reviewRequired,
            Map<String, Object> languageAssistant
    ) {
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                request.task().workflowId(), new BigDecimal("0.91"),
                "READY_FOR_REVIEW", "REVIEW_REQUIRED", "ask_worker",
                "PHASE_3", "STEP_5", Map.of(), List.of("passport_number"),
                List.of(new RenewalRequestedField("passport_number", "DOCUMENT_OCR")),
                null, workerMessage, reviewRequired, failureCode, languageAssistant, null,
                List.of(), List.of(), null, List.of("REVIEW_WORKER_GUIDE"),
                List.of(), null, "rules", "main", List.of()
        );
    }

    private Map<String, Object> reviewLanguageAssistant() {
        Map<String, Object> language = new LinkedHashMap<>();
        language.put("target_language", "vi");
        language.put("generation_status", "warning");
        language.put("requires_human_review", true);
        language.put("standard_korean_text", "여권 사본을 제출해 주세요.");
        language.put("easy_korean_text", "여권을 내 주세요.");
        language.put("translated_text", "Vui lòng nộp bản sao hộ chiếu.");
        language.put("warnings", List.of(Map.of(
                "code", "SEMANTIC_VALIDATION_INCONCLUSIVE"
        )));
        return language;
    }
}
