package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.airun.domain.AiRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiRunResult(
        UUID aiRunId,
        UUID requestId,
        String instruction,
        AiRunStatus status,
        AiAnalysisOutcome analysisOutcome,
        String detectedIntent,
        String errorCode,
        int attemptCount,
        long version,
        List<AiRunQuestionResult> questions,
        List<AiRunCandidateResult> candidates,
        Instant createdAt,
        Instant updatedAt
) {
    public AiRunResult {
        Objects.requireNonNull(aiRunId, "aiRunId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(instruction, "instruction must not be null");
        Objects.requireNonNull(status, "status must not be null");
        questions = List.copyOf(questions);
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
