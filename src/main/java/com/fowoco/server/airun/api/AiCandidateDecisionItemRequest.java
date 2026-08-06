package com.fowoco.server.airun.api;

import com.fowoco.server.airun.domain.AiCandidateDecisionAction;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AiCandidateDecisionItemRequest(
        @NotNull UUID candidateId,
        @NotNull AiCandidateDecisionAction action
) {
}
