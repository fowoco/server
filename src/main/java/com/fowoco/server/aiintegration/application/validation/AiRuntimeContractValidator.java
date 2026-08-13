package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
import com.fowoco.server.aiintegration.application.model.AiIntentDecision;
import com.fowoco.server.aiintegration.application.model.AiQuestion;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.application.model.WorkerContext;
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
    private static final long MAX_DEADLINE_MS = 300_000;
    private static final int MAX_WORKERS = 1;
    private static final int MAX_WORKFLOWS = 20;
    private static final int MAX_CANDIDATES = 50;
    private static final int MAX_QUESTIONS = 50;
    private static final int MAX_CONTEXT_FIELDS = 100;
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final Pattern CANDIDATE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final AiRuntimeBoundaryPolicy boundaryPolicy;

    public AiRuntimeContractValidator(AiRuntimeBoundaryPolicy boundaryPolicy) {
        this.boundaryPolicy = boundaryPolicy;
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
        boundaryPolicy.validateText(request.analysisInput().instruction(), 10_000, true);
        validateAnalysisContext(request);
        switch (request.phase()) {
            case PLAN -> validatePlanInput(request);
            case ANALYZE -> validateAnalyzeInput(request);
        }
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
        int minimumProviderAttempts = request.phase() == AiAnalysisPhase.ANALYZE
                && request.analysisInput().plannedIntentDecision() != null
                ? 0
                : 1;
        if (response.providerAttemptCount() < minimumProviderAttempts
                || response.providerAttemptCount() > 10) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Provider attempt count is invalid.");
        }
        if (response.latencyMs() < 0 || response.latencyMs() > 86_400_000) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime latency is invalid.");
        }
        if (response.candidates().size() > MAX_CANDIDATES) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime returned too many candidates.");
        }
        if (response.questions().size() > MAX_QUESTIONS) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime returned too many questions.");
        }

        Map<String, Set<String>> allowedSlotsByWorkflow = allowedSlotsByWorkflow(request);
        Map<UUID, WorkerContext> allowedWorkers = request.analysisInput().workers().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        WorkerContext::workerRef,
                        worker -> worker
                ));
        Set<String> candidateRefs = new HashSet<>();
        response.candidates().forEach(candidate ->
                validateCandidate(
                        candidate,
                        request.analysisInput().plannedIntentDecision(),
                        allowedWorkers,
                        allowedSlotsByWorkflow,
                        candidateRefs
                ));
        response.validationErrors().forEach(error -> {
            validateIdentifier(error.code(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            boundaryPolicy.validateKey(error.field());
            validateIdentifier(error.field(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        });
        switch (response.outcome()) {
            case OUT_OF_SCOPE -> validateOutOfScopeResponse(request, response);
            case CONTEXT_REQUIRED -> validateContextRequiredResponse(request, response);
            case NEEDS_INFO -> validateNeedsInfoResponse(request, response);
            case REVIEW_REQUIRED -> validateReviewRequiredResponse(response);
        }
    }

    private void validateOutOfScopeResponse(
            AiAnalysisRequest request,
            AiAnalysisResponse response
    ) {
        if (request.phase() != AiAnalysisPhase.PLAN
                || response.contextRequirement() != null
                || !response.questions().isEmpty()
                || !response.candidates().isEmpty()
                || !response.validationErrors().isEmpty()) {
            reject(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "OUT_OF_SCOPE is a PLAN-only terminal response without analysis payloads."
            );
        }
    }

    private void validatePlanInput(AiAnalysisRequest request) {
        if (!request.analysisInput().extractedSlots().isEmpty()
                || !request.analysisInput().requestedFieldKeys().isEmpty()
                || !request.analysisInput().workers().isEmpty()
                || !request.analysisInput().workflowConstraints().isEmpty()
                || request.analysisInput().plannedIntentDecision() != null) {
            reject(
                    AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                    "PLAN request must contain only the HR instruction."
            );
        }
    }

    private void validateAnalyzeInput(AiAnalysisRequest request) {
        if (request.analysisInput().plannedIntentDecision() == null) {
            reject(
                    AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                    "ANALYZE request must preserve the PLAN Intent decision."
            );
        }
        validateIntentDecision(
                request.analysisInput().plannedIntentDecision(),
                AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT
        );
        validateEvidence(
                request.analysisInput().plannedIntentDecision().evidence(),
                request.analysisInput().instruction(),
                AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT
        );
        if (request.analysisInput().requestedFieldKeys().isEmpty()) {
            reject(
                    AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                    "ANALYZE request must identify the context fields requested by the Runtime."
            );
        }
        validateWorkers(request);
        validateWorkflowConstraints(request);
        boolean plannedWorkflowAllowed = request.analysisInput().workflowConstraints().stream()
                .anyMatch(workflow -> workflow.workflowId().equals(
                        request.analysisInput().plannedIntentDecision().workflowId()
                ));
        if (!plannedWorkflowAllowed) {
            reject(
                    AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                    "ANALYZE request PLAN Workflow is outside the active Workflow constraints."
            );
        }
        Set<String> requestedFieldKeys = Set.copyOf(request.analysisInput().requestedFieldKeys());
        request.analysisInput().workers().forEach(worker -> {
            if (!requestedFieldKeys.containsAll(worker.requestedFields().keySet())) {
                reject(
                        AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                        "ANALYZE request contains a field that the Runtime did not request."
                );
            }
        });
    }

    private void validateAnalysisContext(AiAnalysisRequest request) {
        if (request.analysisInput().extractedSlots().size() > MAX_CONTEXT_FIELDS
                || request.analysisInput().requestedFieldKeys().size() > MAX_CONTEXT_FIELDS) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime context field count is invalid.");
        }
        request.analysisInput().extractedSlots().forEach((key, value) -> {
            boundaryPolicy.validateKey(key);
            validateIdentifier(key, AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            boundaryPolicy.validateText(value, 4_000, true);
        });
        Set<String> requestedFieldKeys = new HashSet<>();
        request.analysisInput().requestedFieldKeys().forEach(key -> {
            boundaryPolicy.validateKey(key);
            validateIdentifier(key, AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            if (!requestedFieldKeys.add(key)) {
                reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime field key is duplicated.");
            }
        });
    }

    private void validateWorkers(AiAnalysisRequest request) {
        var workers = request.analysisInput().workers();
        if (workers.isEmpty() || workers.size() > MAX_WORKERS) {
            reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime worker context count is invalid.");
        }
        Set<UUID> workerRefs = new HashSet<>();
        for (WorkerContext worker : workers) {
            if (!workerRefs.add(worker.workerRef())) {
                reject(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Runtime worker reference is duplicated.");
            }
            boundaryPolicy.validateText(worker.displayName(), 120, true);
            boundaryPolicy.validateText(worker.nationalityCode(), 10, false);
            boundaryPolicy.validateText(worker.preferredLanguage(), 32, false);
            boundaryPolicy.validateText(worker.workStatus(), 32, true);
            if (worker.preferredLanguage() != null) {
                validateIdentifier(worker.preferredLanguage(), AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            }
            validateIdentifier(worker.workStatus(), AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            if (worker.requestedFields().size() > 100) {
                reject(
                        AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                        "AI Runtime requested field count is invalid."
                );
            }
            worker.requestedFields().forEach((key, value) -> {
                boundaryPolicy.validateKey(key);
                validateIdentifier(key, AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
                boundaryPolicy.validateText(value, 4_000, true);
            });
        }
    }

    private void validateContextRequiredResponse(
            AiAnalysisRequest request,
            AiAnalysisResponse response
    ) {
        if (response.contextRequirement() == null
                || !response.candidates().isEmpty()
                || !response.questions().isEmpty()) {
            reject(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "CONTEXT_REQUIRED response must include only one context requirement."
            );
        }
        validateContextRequirement(request, response.contextRequirement());
    }

    private void validateContextRequirement(
            AiAnalysisRequest request,
            AiContextRequirement requirement
    ) {
        validateIdentifier(requirement.detectedIntent(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateIdentifier(requirement.workflowId(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateEvidence(
                requirement.evidence(),
                request.analysisInput().instruction(),
                AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT
        );
        validateConfidence(
                requirement.confidence(),
                requirement.confidenceSource(),
                requirement.bertRoutingScore(),
                AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT
        );
        boundaryPolicy.validateText(requirement.agentTarget(), 128, false);
        boundaryPolicy.validateText(requirement.targetDisplayName(), 120, true);
        if (requirement.extractedSlots().size() > MAX_CONTEXT_FIELDS
                || requirement.requiredFieldKeys().isEmpty()
                || requirement.requiredFieldKeys().size() > MAX_CONTEXT_FIELDS) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime context field count is invalid.");
        }
        requirement.extractedSlots().forEach((key, value) -> {
            boundaryPolicy.validateKey(key);
            validateIdentifier(key, AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            boundaryPolicy.validateText(value, 4_000, true);
        });
        Set<String> requiredFieldKeys = new HashSet<>();
        requirement.requiredFieldKeys().forEach(key -> {
            boundaryPolicy.validateKey(key);
            validateIdentifier(key, AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            if (!requiredFieldKeys.add(key)) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime required field key is invalid.");
            }
        });
    }

    private void validateNeedsInfoResponse(AiAnalysisRequest request, AiAnalysisResponse response) {
        if (response.contextRequirement() != null
                || !response.candidates().isEmpty()
                || response.questions().isEmpty()) {
            reject(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "NEEDS_INFO response must include HR questions only."
            );
        }
        Set<String> allowedSlots = allAllowedSlots(request);
        Set<String> questionSlots = new HashSet<>();
        for (AiQuestion question : response.questions()) {
            boundaryPolicy.validateKey(question.slotKey());
            validateIdentifier(question.slotKey(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
            boundaryPolicy.validateText(question.prompt(), 500, true);
            if (!questionSlots.add(question.slotKey())) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime question slot is duplicated.");
            }
            if (!allowedSlots.isEmpty() && !allowedSlots.contains(question.slotKey())) {
                reject(AiRuntimeFailureCode.UNEXPECTED_SLOT, "AI Runtime returned an unexpected question slot.");
            }
        }
    }

    private void validateReviewRequiredResponse(AiAnalysisResponse response) {
        if (response.contextRequirement() != null
                || !response.questions().isEmpty()
                || response.candidates().isEmpty()) {
            reject(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "REVIEW_REQUIRED response must include candidates only."
            );
        }
    }

    private void validateWorkflowConstraints(AiAnalysisRequest request) {
        var workflows = request.analysisInput().workflowConstraints();
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
                boundaryPolicy.validateKey(slot);
                validateIdentifier(slot, AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT);
            });
        }
    }

    private void validateResponseVersions(AiAnalysisRequest request, AiRuntimeVersions versions) {
        validateVersion(versions.agentVersion(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        validateIdentifier(versions.modelProvider(), AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        boundaryPolicy.validateText(versions.modelName(), 128, true);
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
            AiIntentDecision plannedDecision,
            Map<UUID, WorkerContext> allowedWorkers,
            Map<String, Set<String>> allowedSlotsByWorkflow,
            Set<String> candidateRefs
    ) {
        if (!CANDIDATE_REF.matcher(candidate.candidateRef()).matches() || !candidateRefs.add(candidate.candidateRef())) {
            reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime candidate reference is invalid.");
        }
        WorkerContext worker = allowedWorkers.get(candidate.workerRef());
        if (worker == null) {
            reject(AiRuntimeFailureCode.UNEXPECTED_WORKER_REFERENCE, "AI Runtime returned an unknown worker reference.");
        }
        Set<String> allowedSlots = allowedSlotsByWorkflow.get(candidate.workflowId());
        if (allowedSlots == null) {
            reject(AiRuntimeFailureCode.UNEXPECTED_WORKFLOW, "AI Runtime returned an unexpected Workflow.");
        }
        if (plannedDecision == null || !plannedDecision.workflowId().equals(candidate.workflowId())) {
            reject(
                    AiRuntimeFailureCode.UNEXPECTED_WORKFLOW,
                    "AI Runtime changed the Workflow selected during PLAN."
            );
        }
        validateScore(candidate.confidence(), true, AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        candidate.extractedSlots().forEach((key, value) -> {
            validateAllowedSlot(key, allowedSlots);
            boundaryPolicy.validateText(value, 4_000, true);
        });
        validateCoreValues(worker, candidate);
        Set<String> missingSlots = new HashSet<>();
        candidate.missingSlots().forEach(slot -> {
            validateAllowedSlot(slot, allowedSlots);
            if (!missingSlots.add(slot) || candidate.extractedSlots().containsKey(slot)) {
                reject(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "AI Runtime missing slot is invalid.");
            }
        });
    }

    private void validateCoreValues(WorkerContext worker, AiCandidate candidate) {
        String returnedStayExpiryDate = candidate.extractedSlots().get("stay_expiry_date");
        if (returnedStayExpiryDate != null
                && worker.stayExpiryDate() != null
                && !worker.stayExpiryDate().toString().equals(returnedStayExpiryDate)) {
            reject(
                    AiRuntimeFailureCode.CORE_VALUE_MISMATCH,
                    "AI Runtime changed a Server-owned core value."
            );
        }
        worker.requestedFields().forEach((key, originalValue) -> {
            String returnedValue = candidate.extractedSlots().get(key);
            if (returnedValue != null && !originalValue.equals(returnedValue)) {
                reject(
                        AiRuntimeFailureCode.CORE_VALUE_MISMATCH,
                        "AI Runtime changed a Server-owned requested field."
                );
            }
        });
    }

    private void validateAllowedSlot(String slot, Set<String> allowedSlots) {
        boundaryPolicy.validateKey(slot);
        validateIdentifier(slot, AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT);
        if (!allowedSlots.contains(slot)) {
            reject(AiRuntimeFailureCode.UNEXPECTED_SLOT, "AI Runtime returned an unexpected slot.");
        }
    }

    private Map<String, Set<String>> allowedSlotsByWorkflow(AiAnalysisRequest request) {
        Map<String, Set<String>> allowed = new HashMap<>();
        request.analysisInput().workflowConstraints()
                .forEach(workflow -> allowed.put(workflow.workflowId(), workflow.allowedSlotKeys()));
        return Map.copyOf(allowed);
    }

    private Set<String> allAllowedSlots(AiAnalysisRequest request) {
        Set<String> allowedSlots = new HashSet<>();
        request.analysisInput().workflowConstraints()
                .forEach(workflow -> allowedSlots.addAll(workflow.allowedSlotKeys()));
        return Set.copyOf(allowedSlots);
    }

    private void validateIntentDecision(AiIntentDecision decision, AiRuntimeFailureCode failureCode) {
        validateIdentifier(decision.detectedIntent(), failureCode);
        validateIdentifier(decision.workflowId(), failureCode);
        boundaryPolicy.validateText(decision.evidence(), 1_000, false);
        validateConfidence(
                decision.confidence(),
                decision.confidenceSource(),
                decision.bertRoutingScore(),
                failureCode
        );
        boundaryPolicy.validateText(decision.agentTarget(), 128, false);
    }

    private void validateConfidence(
            BigDecimal confidence,
            AiConfidenceSource source,
            BigDecimal bertRoutingScore,
            AiRuntimeFailureCode failureCode
    ) {
        if (source == null) {
            reject(failureCode, "AI Runtime confidence source is missing.");
        }
        if (source == AiConfidenceSource.UNAVAILABLE && confidence != null) {
            reject(failureCode, "Unavailable confidence must be null.");
        }
        if (source != AiConfidenceSource.UNAVAILABLE && confidence == null) {
            reject(failureCode, "Available confidence must include a score.");
        }
        validateScore(confidence, true, failureCode);
        validateScore(bertRoutingScore, true, failureCode);
    }

    private void validateScore(BigDecimal score, boolean nullable, AiRuntimeFailureCode failureCode) {
        if (score == null) {
            if (!nullable) {
                reject(failureCode, "AI Runtime confidence is missing.");
            }
            return;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            reject(failureCode, "AI Runtime confidence is invalid.");
        }
    }

    private void validateEvidence(
            String evidence,
            String instruction,
            AiRuntimeFailureCode failureCode
    ) {
        boundaryPolicy.validateText(evidence, 1_000, false);
        if (evidence != null && !evidence.isBlank() && !instruction.contains(evidence)) {
            reject(failureCode, "AI Runtime evidence is not part of the original instruction.");
        }
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
