package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AiAnalysisContinuationResult(
        UUID attemptId,
        AiAnalysisResponse response,
        Set<String> missingFieldKeys
) {

    public AiAnalysisContinuationResult {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(missingFieldKeys, "missingFieldKeys must not be null");
        missingFieldKeys = Set.copyOf(missingFieldKeys);
    }
}
