package com.fowoco.server.airun.application;

import com.fowoco.server.airun.domain.AiCandidateDecisionAction;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiCandidateDecisionCommand(
        long expectedRunVersion,
        List<Decision> decisions
) {
    public AiCandidateDecisionCommand {
        decisions = List.copyOf(decisions);
    }

    public record Decision(UUID candidateId, AiCandidateDecisionAction action) {
        public Decision {
            Objects.requireNonNull(candidateId);
            Objects.requireNonNull(action);
        }
    }
}
