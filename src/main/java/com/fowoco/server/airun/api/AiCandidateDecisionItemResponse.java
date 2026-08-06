package com.fowoco.server.airun.api;

import com.fowoco.server.airun.application.AiCandidateDecisionResult;
import com.fowoco.server.airun.domain.AiCandidateDecisionAction;
import java.util.UUID;

public record AiCandidateDecisionItemResponse(
        UUID candidateId,
        AiCandidateDecisionAction action
) {
    static AiCandidateDecisionItemResponse from(AiCandidateDecisionResult.Decision result) {
        return new AiCandidateDecisionItemResponse(result.candidateId(), result.action());
    }
}
