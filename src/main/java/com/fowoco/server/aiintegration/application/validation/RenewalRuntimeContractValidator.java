package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.renewal.RenewalRequestedField;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class RenewalRuntimeContractValidator {

    private static final String RENEWAL_INTENT = "EXPIRY_RENEWAL";
    private static final String RENEWAL_WORKFLOW = "WF-STY-001";
    private static final Set<String> SCENARIOS = Set.of(
            "ask_hr", "ask_worker", "generate", "ocr", "out_of_scope"
    );
    private static final Set<String> CASE_SIGNALS = Set.of(
            "RUN_OCR",
            "NEEDS_INFO",
            "REQUEST_CONTRACT_SLOTS",
            "REQUEST_IDENTITY_DOCUMENT",
            "GENERATE_DRAFTS",
            "READY_FOR_REVIEW",
            "OCR_SAVED",
            "CANCEL_OUT_OF_SCOPE"
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");

    private final AiRuntimeBoundaryPolicy boundaryPolicy;

    public RenewalRuntimeContractValidator(AiRuntimeBoundaryPolicy boundaryPolicy) {
        this.boundaryPolicy = boundaryPolicy;
    }

    public void validateRequest(RenewalRunRequest request) {
        if (request == null
                || request.requestId() == null
                || request.taskId() == null
                || request.workerId() == null
                || request.companyId() == null
                || request.worker() == null
                || request.company() == null
                || request.task() == null) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "Renewal request context is missing.");
        }
        boundaryPolicy.validateText(request.instruction(), 10_000, true);
        if (!request.taskId().equals(request.task().taskId())
                || !request.workerId().equals(request.worker().workerId())
                || !request.companyId().equals(request.company().companyId())
                || !request.companyId().equals(request.worker().companyId())) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "Renewal request identifiers do not match.");
        }
        if (!RENEWAL_WORKFLOW.equals(request.task().workflowId())) {
            reject(AiRuntimeFailureCode.UNEXPECTED_WORKFLOW, "Renewal task Workflow is invalid.");
        }
        if (request.slots().size() > 100 || request.documents().size() > 20) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "Renewal request is too large.");
        }
        request.slots().forEach((key, value) -> {
            validateIdentifier(key, AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            if (value instanceof String text) {
                boundaryPolicy.validateText(text, 4_000, false);
            }
        });
    }

    public void validateResponse(RenewalRunRequest request, RenewalRunResponse response) {
        validateRequest(request);
        if (response == null) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal response is missing.");
        }
        if (!request.requestId().equals(response.requestId())) {
            reject(AiRuntimeFailureCode.REQUEST_ID_MISMATCH, "Renewal response requestId does not match.");
        }
        if (!request.taskId().equals(response.taskId())) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal response taskId does not match.");
        }
        if (!RENEWAL_INTENT.equals(response.intent()) || !RENEWAL_WORKFLOW.equals(response.workflowId())) {
            reject(AiRuntimeFailureCode.UNEXPECTED_WORKFLOW, "Renewal response Intent or Workflow is invalid.");
        }
        if (response.confidence() == null
                || response.confidence().compareTo(BigDecimal.ZERO) < 0
                || response.confidence().compareTo(BigDecimal.ONE) > 0) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal confidence is invalid.");
        }
        if (response.scenario() == null || !SCENARIOS.contains(response.scenario())) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal scenario is invalid.");
        }
        boundaryPolicy.validateText(response.status(), 40, true);
        boundaryPolicy.validateText(response.outcome(), 80, true);
        boundaryPolicy.validateText(response.workerRequestMessage(), 1_000, false);
        validateLanguageAssistant(response);
        if (response.missingSlots().size() > 100
                || response.requestedFields().size() > 100
                || response.caseSignals().size() > 20
                || response.generatedDocuments().size() > 20
                || response.errors().size() > 20) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal response is too large.");
        }
        Set<String> missing = new HashSet<>();
        response.missingSlots().forEach(key -> {
            validateIdentifier(key, AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            if (!missing.add(key)) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal missing slot is duplicated.");
            }
        });
        Set<String> requested = new HashSet<>();
        for (RenewalRequestedField field : response.requestedFields()) {
            if (field == null) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal requested field is missing.");
            }
            validateIdentifier(field.key(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            validateIdentifier(field.sourceHint(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            if (!requested.add(field.key()) || !missing.contains(field.key())) {
                reject(AiRuntimeFailureCode.UNEXPECTED_SLOT, "Renewal requested field is invalid.");
            }
        }
        response.caseSignals().forEach(signal -> {
            if (!CASE_SIGNALS.contains(signal)) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Renewal Case signal is invalid.");
            }
        });
        validateScenario(response);
    }

    private void validateLanguageAssistant(RenewalRunResponse response) {
        if (response.languageAssistant() == null) {
            return;
        }
        Object language = response.languageAssistant().get("target_language");
        if (!(language instanceof String value) || value.isBlank() || value.length() > 20) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Language Assistant target is invalid.");
        }
        for (String key : Set.of("standard_korean_text", "easy_korean_text", "translated_text")) {
            Object text = response.languageAssistant().get(key);
            if (text instanceof String value) {
                boundaryPolicy.validateText(value, 1_000, false);
            }
        }
    }

    private void validateScenario(RenewalRunResponse response) {
        if ("ask_hr".equals(response.scenario()) && response.missingSlots().isEmpty()) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "ask_hr requires missing slots.");
        }
        if ("ask_worker".equals(response.scenario())
                && (response.workerRequestMessage() == null || response.workerRequestMessage().isBlank())) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "ask_worker requires a worker message.");
        }
        if ("generate".equals(response.scenario()) && response.generatedDocuments().isEmpty()) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "generate requires document descriptors.");
        }
    }

    private void validateIdentifier(String value, AiRuntimeFailureCode code) {
        boundaryPolicy.validateKey(value);
        if (!IDENTIFIER.matcher(value).matches()) {
            reject(code, "Renewal identifier is invalid.");
        }
    }

    private void reject(AiRuntimeFailureCode code, String message) {
        throw new AiRuntimeContractException(code, message);
    }
}
