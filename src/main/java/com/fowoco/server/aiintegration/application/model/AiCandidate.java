package com.fowoco.server.aiintegration.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Untrusted Workflow candidate returned by the AI Runtime.
 */
public record AiCandidate(
        String candidateRef,
        UUID workerRef,
        String workflowId,
        Map<String, String> extractedSlots,
        List<String> missingSlots,
        BigDecimal confidence
) {

    public AiCandidate {
        Objects.requireNonNull(candidateRef, "candidateRef must not be null");
        Objects.requireNonNull(workerRef, "workerRef must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(extractedSlots, "extractedSlots must not be null");
        Objects.requireNonNull(missingSlots, "missingSlots must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        extractedSlots = Map.copyOf(extractedSlots);
        missingSlots = List.copyOf(missingSlots);
    }
}
