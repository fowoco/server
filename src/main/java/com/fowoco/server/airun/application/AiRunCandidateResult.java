package com.fowoco.server.airun.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AiRunCandidateResult(
        UUID candidateId,
        String candidateRef,
        UUID workerId,
        String workflowId,
        Map<String, String> extractedSlots,
        List<String> missingSlots,
        BigDecimal confidence
) {
    public AiRunCandidateResult {
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        Objects.requireNonNull(candidateRef, "candidateRef must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        extractedSlots = Map.copyOf(extractedSlots);
        missingSlots = List.copyOf(missingSlots);
        Objects.requireNonNull(confidence, "confidence must not be null");
    }
}
