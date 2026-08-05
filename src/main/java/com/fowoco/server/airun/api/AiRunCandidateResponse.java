package com.fowoco.server.airun.api;

import com.fowoco.server.airun.application.AiRunCandidateResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiRunCandidateResponse(
        UUID candidateId,
        String candidateRef,
        UUID workerId,
        String workflowId,
        Map<String, String> extractedSlots,
        List<String> missingSlots,
        BigDecimal confidence
) {
    static AiRunCandidateResponse from(AiRunCandidateResult result) {
        return new AiRunCandidateResponse(
                result.candidateId(),
                result.candidateRef(),
                result.workerId(),
                result.workflowId(),
                result.extractedSlots(),
                result.missingSlots(),
                result.confidence()
        );
    }
}
