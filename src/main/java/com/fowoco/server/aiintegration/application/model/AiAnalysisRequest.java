package com.fowoco.server.aiintegration.application.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-internal command for one managed PLAN or ANALYZE attempt.
 *
 * <p>The HTTP Adapter selects the smaller Runtime payload from this command.</p>
 */
public record AiAnalysisRequest(
        UUID requestId,
        UUID attemptId,
        AiAnalysisPhase phase,
        String contractVersion,
        String requiredKnowledgeVersion,
        long deadlineMs,
        AnalysisInput analysisInput
) {

    public AiAnalysisRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(contractVersion, "contractVersion must not be null");
        Objects.requireNonNull(requiredKnowledgeVersion, "requiredKnowledgeVersion must not be null");
        Objects.requireNonNull(analysisInput, "analysisInput must not be null");
    }
}
