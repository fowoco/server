package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import java.util.Objects;
import java.util.UUID;

public record AiRunCreation(
        UUID aiRunId,
        UUID companyId,
        AiAnalysisRequest request,
        boolean newlyCreated
) {
    public AiRunCreation {
        Objects.requireNonNull(aiRunId, "aiRunId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(request, "request must not be null");
    }
}
