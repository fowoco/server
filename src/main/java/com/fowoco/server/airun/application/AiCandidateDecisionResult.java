package com.fowoco.server.airun.application;

import com.fowoco.server.airun.domain.AiCandidateDecisionAction;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiCandidateDecisionResult(
        UUID decisionBatchId,
        UUID aiRunId,
        UUID caseId,
        List<UUID> taskIds,
        List<Decision> decisions,
        long runVersion
) {
    public AiCandidateDecisionResult {
        Objects.requireNonNull(decisionBatchId);
        Objects.requireNonNull(aiRunId);
        taskIds = List.copyOf(taskIds);
        decisions = List.copyOf(decisions);
    }

    public record Decision(UUID candidateId, AiCandidateDecisionAction action) {
        public Decision {
            Objects.requireNonNull(candidateId);
            Objects.requireNonNull(action);
        }
    }
}
