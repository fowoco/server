package com.fowoco.server.casework.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.casework.application.CaseProgress;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CaseProgressResponse(int completedSteps, int totalSteps, int percentage) {
    static CaseProgressResponse from(CaseProgress progress) {
        return new CaseProgressResponse(
                progress.completedSteps(),
                progress.totalSteps(),
                progress.percentage()
        );
    }
}
