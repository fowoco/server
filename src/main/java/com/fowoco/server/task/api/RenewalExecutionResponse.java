package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.aiintegration.application.renewal.RenewalRequestedField;
import com.fowoco.server.task.application.renewal.GeneratedDocumentResult;
import com.fowoco.server.task.application.renewal.RenewalExecutionResult;
import com.fowoco.server.task.domain.TaskStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RenewalExecutionResponse(
        UUID requestId,
        UUID taskId,
        TaskStatus taskStatus,
        long taskVersion,
        String intent,
        String workflowId,
        BigDecimal confidence,
        String scenario,
        String outcome,
        List<String> missingSlots,
        List<RenewalRequestedField> requestedFields,
        List<String> caseSignals,
        List<GeneratedDocumentResult> generatedDocuments,
        UUID workerMessageDraftId,
        Long workerMessageDraftVersion,
        boolean humanReviewRequired
) {
    static RenewalExecutionResponse from(RenewalExecutionResult result) {
        return new RenewalExecutionResponse(
                result.agentResult().requestId(),
                result.task().taskId(),
                result.task().status(),
                result.task().version(),
                result.agentResult().intent(),
                result.agentResult().workflowId(),
                result.agentResult().confidence(),
                result.agentResult().scenario(),
                result.agentResult().outcome(),
                result.agentResult().missingSlots(),
                result.agentResult().requestedFields(),
                result.agentResult().caseSignals(),
                result.generatedDocuments(),
                result.workerMessageDraft() == null ? null : result.workerMessageDraft().draftId(),
                result.workerMessageDraft() == null ? null : result.workerMessageDraft().version(),
                true
        );
    }

}
