package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.aiintegration.application.model.AiIntentDecision;
import com.fowoco.server.aiintegration.application.model.WorkerContext;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.fowoco.server.airun.application.error.AiContextResolutionException;
import com.fowoco.server.airun.application.error.AiContextResolutionFailureCode;
import com.fowoco.server.airun.application.port.AiAttemptStarter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.fowoco.server.airun.application.AiRunExecutionTelemetry.Phase.ANALYZE;
import static com.fowoco.server.airun.application.AiRunExecutionTelemetry.Stage.ANALYZE_RUNTIME_CALL;
import static com.fowoco.server.airun.application.AiRunExecutionTelemetry.Stage.SLOT_RESOLUTION;

/**
 * Continues a validated CONTEXT_REQUIRED result without holding a database transaction open.
 * #24 wires this service to a durable AiAttempt implementation.
 */
public final class AiAnalysisContinuationService {

    public static final int MAX_CONTEXT_ROUNDS = 2;

    private final AiSlotResolutionTransaction slotResolutionTransaction;
    private final AiAttemptStarter attemptStarter;
    private final AiRuntimeClient runtimeClient;
    private final AiRunExecutionTelemetry telemetry;

    public AiAnalysisContinuationService(
            AiSlotResolutionTransaction slotResolutionTransaction,
            AiAttemptStarter attemptStarter,
            AiRuntimeClient runtimeClient,
            AiRunExecutionTelemetry telemetry
    ) {
        this.slotResolutionTransaction = Objects.requireNonNull(
                slotResolutionTransaction,
                "slotResolutionTransaction must not be null"
        );
        this.attemptStarter = Objects.requireNonNull(attemptStarter, "attemptStarter must not be null");
        this.runtimeClient = Objects.requireNonNull(runtimeClient, "runtimeClient must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    public AiAnalysisContinuationResult continueAnalysis(
            UUID companyId,
            AiAnalysisRequest previousRequest,
            AiAnalysisResponse previousResponse,
            int completedContextRounds,
            long remainingDeadlineMs,
            AiRuntimeCallContext callContext
    ) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(previousRequest, "previousRequest must not be null");
        Objects.requireNonNull(previousResponse, "previousResponse must not be null");
        Objects.requireNonNull(callContext, "callContext must not be null");
        validateContinuation(previousRequest, previousResponse, completedContextRounds);

        AiSlotResolution resolution = telemetry.measure(
                previousRequest.requestId(),
                previousRequest.attemptId(),
                ANALYZE,
                SLOT_RESOLUTION,
                () -> slotResolutionTransaction.resolve(
                        companyId,
                        previousRequest.requiredKnowledgeVersion(),
                        previousResponse.contextRequirement()
                )
        );
        validateSameWorker(previousRequest, resolution.worker());

        int nextContextRound = completedContextRounds + 1;
        AnalysisInput analyzeInput = buildAnalyzeInput(
                previousRequest.analysisInput(),
                previousResponse,
                resolution
        );
        UUID attemptId = attemptStarter.startAttempt(
                companyId,
                previousRequest.requestId(),
                AiAnalysisPhase.ANALYZE,
                nextContextRound,
                analyzeInput
        );
        AiAnalysisRequest analyzeRequest = new AiAnalysisRequest(
                previousRequest.requestId(),
                attemptId,
                AiAnalysisPhase.ANALYZE,
                previousRequest.contractVersion(),
                previousRequest.requiredKnowledgeVersion(),
                remainingDeadlineMs,
                analyzeInput
        );
        AiAnalysisResponse response = telemetry.measure(
                analyzeRequest.requestId(),
                analyzeRequest.attemptId(),
                ANALYZE,
                ANALYZE_RUNTIME_CALL,
                () -> runtimeClient.analyze(analyzeRequest, callContext)
        );
        return new AiAnalysisContinuationResult(
                attemptId,
                response,
                resolution.missingFieldKeys()
        );
    }

    private AnalysisInput buildAnalyzeInput(
            AnalysisInput previousInput,
            AiAnalysisResponse previousResponse,
            AiSlotResolution resolution
    ) {
        Map<String, String> extractedSlots = new LinkedHashMap<>(previousInput.extractedSlots());
        extractedSlots.putAll(previousResponse.contextRequirement().extractedSlots());

        LinkedHashSet<String> requestedFieldKeys = new LinkedHashSet<>(previousInput.requestedFieldKeys());
        requestedFieldKeys.addAll(previousResponse.contextRequirement().requiredFieldKeys());

        Map<String, String> requestedFields = new LinkedHashMap<>();
        if (!previousInput.workers().isEmpty()) {
            requestedFields.putAll(previousInput.workers().get(0).requestedFields());
        }
        requestedFields.putAll(resolution.resolvedFields());
        WorkerContext worker = resolution.worker();
        WorkerContext mergedWorker = new WorkerContext(
                worker.workerRef(),
                worker.displayName(),
                worker.nationalityCode(),
                worker.preferredLanguage(),
                worker.workStatus(),
                worker.stayExpiryDate(),
                worker.contractStartDate(),
                worker.contractEndDate(),
                requestedFields
        );
        return new AnalysisInput(
                previousInput.instruction(),
                extractedSlots,
                new ArrayList<>(requestedFieldKeys),
                List.of(mergedWorker),
                resolution.workflowConstraints(),
                AiIntentDecision.from(previousResponse.contextRequirement())
        );
    }

    private void validateContinuation(
            AiAnalysisRequest previousRequest,
            AiAnalysisResponse previousResponse,
            int completedContextRounds
    ) {
        if (!previousRequest.requestId().equals(previousResponse.requestId())
                || previousResponse.outcome() != AiAnalysisOutcome.CONTEXT_REQUIRED
                || previousResponse.contextRequirement() == null) {
            reject(
                    AiContextResolutionFailureCode.INVALID_CONTEXT_RESPONSE,
                    "Only a matching CONTEXT_REQUIRED response can continue analysis."
            );
        }
        if (completedContextRounds < 0 || completedContextRounds >= MAX_CONTEXT_ROUNDS) {
            reject(
                    AiContextResolutionFailureCode.CONTEXT_ROUND_LIMIT,
                    "The automatic context resolution round limit was reached."
            );
        }
    }

    private void validateSameWorker(AiAnalysisRequest previousRequest, WorkerContext resolvedWorker) {
        if (previousRequest.analysisInput().workers().isEmpty()) {
            return;
        }
        UUID previousWorkerRef = previousRequest.analysisInput().workers().get(0).workerRef();
        if (!previousWorkerRef.equals(resolvedWorker.workerRef())) {
            reject(
                    AiContextResolutionFailureCode.TARGET_CHANGED,
                    "The Runtime attempted to change the Worker target during one analysis."
            );
        }
    }

    private void reject(AiContextResolutionFailureCode failureCode, String safeMessage) {
        throw new AiContextResolutionException(failureCode, safeMessage);
    }
}
