package com.fowoco.server.aiintegration.application.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.renewal.RenewalCompanySnapshot;
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
    void rejectsAnIntentUsedAsAWorkflowId() {
        RenewalRunRequest request = request();
        RenewalRunResponse valid = response(request);
        RenewalRunResponse invalid = new RenewalRunResponse(
                valid.requestId(), valid.attemptId(), valid.taskId(), valid.intent(),
                "EXPIRY_RENEWAL", valid.confidence(), valid.status(), valid.outcome(),
                valid.scenario(), valid.phase(), valid.step(), valid.slots(), valid.missingSlots(),
                valid.requestedFields(), valid.guideMessage(), valid.workerRequestMessage(),
                valid.languageAssistant(), valid.ocrResult(), valid.generatedDocuments(), valid.evidence(),
                valid.documentValidation(), valid.caseSignals(), valid.progressEvents(),
                valid.supervisorReason(), valid.supervisorSource(), valid.activeSubgraph(), valid.errors()
        );

        assertThatThrownBy(() -> validator.validateResponse(request, invalid))
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
                valid.guideMessage(), "여권과 외국인등록증을 제출해 주세요.", language,
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
    void acceptsTheAgentOutOfScopeResultWithoutTreatingItAsRenewal() {
        RenewalRunRequest request = request();
        RenewalRunResponse valid = response(request);
        RenewalRunResponse outOfScope = new RenewalRunResponse(
                valid.requestId(), valid.attemptId(), valid.taskId(), "OUT_OF_SCOPE", "",
                new BigDecimal("0.93"), "CANCELLED", "OUT_OF_SCOPE", "out_of_scope",
                "PHASE_1", "STEP_2", Map.of(), List.of(), List.of(),
                "지원 범위를 벗어난 요청입니다.", null, null, null, List.of(), List.of(),
                null, List.of("CANCEL_OUT_OF_SCOPE"), List.of(), null, "rules", "main",
                List.of()
        );

        assertThatCode(() -> validator.validateResponse(request, outOfScope))
                .doesNotThrowAnyException();
    }

    private RenewalRunRequest request() {
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
                        taskId, companyId, workerId, UUID.randomUUID(), "RECONTRACT", "WF-STY-001",
                        "0.2.0", "재계약", null, Map.of(), 0, "AI_ANALYZED", "DRAFT", null,
                        UUID.randomUUID(), UUID.randomUUID(), now, now, 0
                )
        );
    }

    private RenewalRunResponse response(RenewalRunRequest request) {
        return new RenewalRunResponse(
                request.requestId(), request.attemptId(), request.taskId(), "EXPIRY_RENEWAL",
                "WF-STY-001", new BigDecimal("0.91"), "NEEDS_INFO", "NEEDS_INFO", "ask_hr",
                "PHASE_2", "STEP_5", Map.of(), List.of("wage"),
                List.of(new RenewalRequestedField("wage", "USER_INPUT")),
                "임금을 확인해 주세요.", null, null, null, List.of(), List.of(), null,
                List.of("REQUEST_CONTRACT_SLOTS", "NEEDS_INFO"), List.of(), null, "rules", "main",
                List.of()
        );
    }
}
