package com.fowoco.server.aiintegration.application.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral body for one Server-managed AI attempt.
 */
public record AiAnalysisRequest(
        UUID requestId,
        UUID attemptId,
        String contractVersion,
        String requiredKnowledgeVersion,
        long deadlineMs,
        MaskedAnalysisInput maskedInput
) {

    public AiAnalysisRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(contractVersion, "contractVersion must not be null");
        Objects.requireNonNull(requiredKnowledgeVersion, "requiredKnowledgeVersion must not be null");
        Objects.requireNonNull(maskedInput, "maskedInput must not be null");
    }
}
