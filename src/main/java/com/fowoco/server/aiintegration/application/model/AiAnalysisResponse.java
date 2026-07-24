package com.fowoco.server.aiintegration.application.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Untrusted response returned by the AI Runtime and validated again by the Server.
 */
public record AiAnalysisResponse(
        UUID requestId,
        AiAnalysisOutcome outcome,
        List<AiCandidate> candidates,
        List<AiValidationError> validationErrors,
        AiRuntimeVersions versions,
        int providerAttemptCount,
        long latencyMs
) {

    public AiAnalysisResponse {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(validationErrors, "validationErrors must not be null");
        Objects.requireNonNull(versions, "versions must not be null");
        candidates = List.copyOf(candidates);
        validationErrors = List.copyOf(validationErrors);
    }
}
