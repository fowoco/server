package com.fowoco.server.aiintegration.infrastructure.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.aiintegration.application.model.AiIntentDecision;
import com.fowoco.server.aiintegration.application.model.WorkerContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal JSON body sent across the Server-to-Runtime boundary.
 *
 * <p>Attempt identifiers, version requirements, deadlines, extracted slots, and workflow
 * constraints remain Server-owned metadata. The PLAN decision is serialized only as
 * plannedIntent, plannedWorkflowId, and an optional agentTarget so ANALYZE reuses the PLAN
 * decision instead of classifying or routing the instruction again.</p>
 */
record AiRuntimeHttpRequest(
        UUID requestId,
        AiAnalysisPhase phase,
        HttpAnalysisInput analysisInput
) {

    static AiRuntimeHttpRequest from(AiAnalysisRequest request) {
        return new AiRuntimeHttpRequest(
                request.requestId(),
                request.phase(),
                HttpAnalysisInput.from(request.analysisInput())
        );
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    record HttpAnalysisInput(
            String instruction,
            String plannedIntent,
            String plannedWorkflowId,
            String agentTarget,
            List<String> requestedFieldKeys,
            List<HttpWorkerContext> workers
    ) {

        static HttpAnalysisInput from(AnalysisInput input) {
            AiIntentDecision decision = input.plannedIntentDecision();
            return new HttpAnalysisInput(
                    input.instruction(),
                    decision == null ? null : decision.detectedIntent(),
                    decision == null ? null : decision.workflowId(),
                    decision == null ? null : decision.agentTarget(),
                    input.requestedFieldKeys(),
                    input.workers().stream().map(HttpWorkerContext::from).toList()
            );
        }
    }

    record HttpWorkerContext(
            UUID workerRef,
            Map<String, String> requestedFields
    ) {

        static HttpWorkerContext from(WorkerContext worker) {
            return new HttpWorkerContext(worker.workerRef(), worker.requestedFields());
        }
    }
}
