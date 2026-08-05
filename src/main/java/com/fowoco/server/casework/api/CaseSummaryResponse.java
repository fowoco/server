package com.fowoco.server.casework.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.casework.application.CaseProjection;
import com.fowoco.server.casework.domain.CaseDisplayStatus;
import com.fowoco.server.casework.domain.CasePriority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CaseSummaryResponse(
        UUID caseId,
        UUID workerId,
        String workerDisplayName,
        String title,
        CaseDisplayStatus displayStatus,
        boolean hasUnreadResponse,
        CasePriority priority,
        CaseProgressResponse progress,
        LocalDate dueDate,
        CaseTaskResponse currentTask,
        Instant updatedAt
) {
    static CaseSummaryResponse from(CaseProjection projection) {
        return new CaseSummaryResponse(
                projection.caseId(),
                projection.workerId(),
                projection.workerDisplayName(),
                projection.title(),
                projection.displayStatus(),
                projection.hasUnreadResponse(),
                projection.priority(),
                CaseProgressResponse.from(projection.progress()),
                projection.dueDate(),
                CaseTaskResponse.from(projection.currentTask()),
                projection.updatedAt()
        );
    }
}
