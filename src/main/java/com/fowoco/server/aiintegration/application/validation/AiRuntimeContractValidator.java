package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.application.model.MaskedWorkerContext;
import com.fowoco.server.aiintegration.application.model.WorkflowConstraint;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Semantic validation applied before an outbound call and after an untrusted Runtime response.
 */
@Component
public class AiRuntimeContractValidator {

    private static final long MIN_DEADLINE_MS = 100;
    private static final long MAX_DEADLINE_MS = 60_000;
    private static final int MAX_WORKERS = 20;
    private static final int MAX_WORKFLOWS = 20;
    private static final int MAX_CANDIDATES = 50;
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final Pattern CANDIDATE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final AiRuntimePrivacyPolicy privacyPolicy;

    public AiRuntimeContractValidator(AiRuntimePrivacyPolicy privacyPolicy) {
        this.privacyPolicy = privacyPolicy;
    }

    public void validateRequest(AiAnalysisRequest request) {
        if (request == null) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime request is missing.");
        }
        validateVersion(request.contractVersion(), AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
        validateVersion(request.requiredKnowledgeVersion(), AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
        if (request.deadlineMs() < MIN_DEADLINE_MS || request.deadlineMs() > MAX_DEADLINE_MS) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime deadline is outside the allowed range.");
        }
        privacyPolicy.validateText(request.maskedInput().maskedInstruction(), 10_000, true);
        validateWorkers(request);
        validateWorkflowConstraints(request);
    }

    public void validateResponse(AiAnalysisRequest request, AiAnalysisResponse response) {
        validateRequest(request);
        if (response == null) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime response is missing.");
        }
        if (!request.requestId().equals(response.requestId())) {
            reject(AiRuntimeFailureCode.REQUEST_ID_MISMATCH, "AI Runtime response requestId does not match.");
        }
        validateResponseVersions(request, response.versions());
        if (response.providerAttemptCount() < 1 || response.providerAttemptCount() > 10) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Provider attempt count is invalid.");
        }
        if (response.latencyMs() < 0 || response.latencyMs() > 86_400_000) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime latency is invalid.");
        }
        if (response.candidates().size() > MAX_CANDIDATES) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime returned too many candidates.");
        }

        Map<String, Set<String>> allowedSlotsByWorkflow = allowedSlotsByWorkflow(request);
        Set<UUID> allowedWorkers = request.maskedInput().workers().stream()
                .map(MaskedWorkerContext::workerRef)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> candidateRefs = new HashSet<>();
        response.candidates().forEach(candidate ->
                validateCandidate(candidate, allowedWorkers, allowedSlotsByWorkflow, candidateRefs));
        response.validationErrors().forEach(error -> {
            validateIdentifier(error.code(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            privacyPolicy.validateKey(error.field());
            validateIdentifier(error.field(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        });
        if (response.outcome() == com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome.REVIEW_REQUIRED
                && response.candidates().isEmpty()) {
            reject(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "REVIEW_REQUIRED response must include at least one candidate."
            );
        }
    }

    private void validateWorkers(AiAnalysisRequest request) {
        var workers = request.maskedInput().workers();
        if (workers.isEmpty() || workers.size() > MAX_WORKERS) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime worker context count is invalid.");
        }
        Set<UUID> workerRefs = new HashSet<>();
        for (MaskedWorkerContext worker : workers) {
            if (!workerRefs.add(worker.workerRef())) {
                reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime worker reference is duplicated.");
            }
            privacyPolicy.validateText(worker.preferredLanguage(), 32, true);
            privacyPolicy.validateText(worker.workStatus(), 32, true);
            validateIdentifier(worker.preferredLanguage(), AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            validateIdentifier(worker.workStatus(), AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
        }
    }

    private void validateWorkflowConstraints(AiAnalysisRequest request) {
        var workflows = request.maskedInput().workflowConstraints();
        if (workflows.isEmpty() || workflows.size() > MAX_WORKFLOWS) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime Workflow constraint count is invalid.");
        }
        Set<String> workflowIds = new HashSet<>();
        for (WorkflowConstraint workflow : workflows) {
            validateIdentifier(workflow.workflowId(), AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            if (!workflowIds.add(workflow.workflowId()) || workflow.allowedSlotKeys().size() > 100) {
                reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime Workflow constraint is invalid.");
            }
            workflow.allowedSlotKeys().forEach(slot -> {
                privacyPolicy.validateKey(slot);
                validateIdentifier(slot, AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            });
        }
    }

    private void validateResponseVersions(AiAnalysisRequest request, AiRuntimeVersions versions) {
        validateVersion(versions.agentVersion(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateIdentifier(versions.modelProvider(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        privacyPolicy.validateText(versions.modelName(), 128, true);
        validateVersion(versions.modelVersion(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateVersion(versions.promptVersion(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateVersion(versions.contextPackVersion(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateVersion(versions.workflowCatalogVersion(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateVersion(versions.contractVersion(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);

        if (!request.contractVersion().equals(versions.contractVersion())) {
            reject(AiRuntimeFailureCode.CONTRACT_VERSION_MISMATCH, "AI Runtime contract version does not match.");
        }
        if (!request.requiredKnowledgeVersion().equals(versions.workflowCatalogVersion())) {
            reject(AiRuntimeFailureCode.KNOWLEDGE_VERSION_MISMATCH, "AI Runtime Knowledge version does not match.");
        }
    }

    private void validateCandidate(
            AiCandidate candidate,
            Set<UUID> allowedWorkers,
            Map<String, Set<String>> allowedSlotsByWorkflow,
            Set<String> candidateRefs
    ) {
        if (!CANDIDATE_REF.matcher(candidate.candidateRef()).matches() || !candidateRefs.add(candidate.candidateRef())) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime candidate reference is invalid.");
        }
        if (!allowedWorkers.contains(candidate.workerRef())) {
            reject(AiRuntimeFailureCode.UNEXPECTED_WORKER_REFERENCE, "AI Runtime returned an unknown worker reference.");
        }
        Set<String> allowedSlots = allowedSlotsByWorkflow.get(candidate.workflowId());
        if (allowedSlots == null) {
            reject(AiRuntimeFailureCode.UNEXPECTED_WORKFLOW, "AI Runtime returned an unexpected Workflow.");
        }
        if (candidate.confidence().compareTo(BigDecimal.ZERO) < 0
                || candidate.confidence().compareTo(BigDecimal.ONE) > 0) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime confidence is invalid.");
        }
        candidate.extractedSlots().forEach((key, value) -> {
            validateAllowedSlot(key, allowedSlots);
            privacyPolicy.validateText(value, 4_000, true);
        });
        Set<String> missingSlots = new HashSet<>();
        candidate.missingSlots().forEach(slot -> {
            validateAllowedSlot(slot, allowedSlots);
            if (!missingSlots.add(slot) || candidate.extractedSlots().containsKey(slot)) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime missing slot is invalid.");
            }
        });
    }

    private void validateAllowedSlot(String slot, Set<String> allowedSlots) {
        privacyPolicy.validateKey(slot);
        validateIdentifier(slot, AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        if (!allowedSlots.contains(slot)) {
            reject(AiRuntimeFailureCode.UNEXPECTED_SLOT, "AI Runtime returned an unexpected slot.");
        }
    }

    private Map<String, Set<String>> allowedSlotsByWorkflow(AiAnalysisRequest request) {
        Map<String, Set<String>> allowed = new HashMap<>();
        request.maskedInput().workflowConstraints()
                .forEach(workflow -> allowed.put(workflow.workflowId(), workflow.allowedSlotKeys()));
        return Map.copyOf(allowed);
    }

    private void validateVersion(String version, AiRuntimeFailureCode failureCode) {
        if (version == null || !VERSION.matcher(version).matches()) {
            reject(failureCode, "AI Runtime version is invalid.");
        }
    }

    private void validateIdentifier(String identifier, AiRuntimeFailureCode failureCode) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            reject(failureCode, "AI Runtime identifier is invalid.");
        }
    }

    private void reject(AiRuntimeFailureCode failureCode, String safeMessage) {
        throw new AiRuntimeContractException(failureCode, safeMessage);
    }
}
