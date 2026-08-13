package com.fowoco.server.airun.api;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.airun.application.AiRunResult;
import com.fowoco.server.airun.domain.AiRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiRunResponse(
        UUID aiRunId,
        UUID requestId,
        String instruction,
        AiRunStatus status,
        AiAnalysisOutcome analysisOutcome,
        String detectedIntent,
        String errorCode,
        int attemptCount,
        long version,
        List<AiRunQuestionResponse> questions,
        List<AiRunCandidateResponse> candidates,
        Instant createdAt,
        Instant updatedAt
) {
    public static AiRunResponse from(AiRunResult result) {
        return new AiRunResponse(
                result.aiRunId(),
                result.requestId(),
                result.instruction(),
                result.status(),
                result.analysisOutcome(),
                result.detectedIntent(),
                AiRunPublicErrorCode.fromInternal(result.errorCode()),
                result.attemptCount(),
                result.version(),
                result.questions().stream().map(AiRunQuestionResponse::from).toList(),
                result.candidates().stream().map(AiRunCandidateResponse::from).toList(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
