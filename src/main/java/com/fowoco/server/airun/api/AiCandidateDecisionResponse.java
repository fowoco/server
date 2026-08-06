package com.fowoco.server.airun.api;

import com.fowoco.server.airun.application.AiCandidateDecisionResult;
import java.util.List;
import java.util.UUID;

public record AiCandidateDecisionResponse(
        UUID decisionBatchId,
        UUID aiRunId,
        UUID caseId,
        List<UUID> taskIds,
        List<AiCandidateDecisionItemResponse> decisions,
        long runVersion
) {
    static AiCandidateDecisionResponse from(AiCandidateDecisionResult result) {
        return new AiCandidateDecisionResponse(
                result.decisionBatchId(),
                result.aiRunId(),
                result.caseId(),
                result.taskIds(),
                result.decisions().stream().map(AiCandidateDecisionItemResponse::from).toList(),
                result.runVersion()
        );
    }
}
