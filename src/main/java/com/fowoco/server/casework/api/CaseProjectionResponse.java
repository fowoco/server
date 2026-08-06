package com.fowoco.server.casework.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.casework.application.CaseProjection;
import com.fowoco.server.casework.domain.CaseDisplayStatus;
import com.fowoco.server.casework.domain.CaseLifecycleStatus;
import com.fowoco.server.casework.domain.CasePriority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CaseProjectionResponse(
        UUID caseId,
        UUID workerId,
        String workerDisplayName,
        String title,
        CaseLifecycleStatus lifecycleStatus,
        CaseDisplayStatus displayStatus,
        boolean hasUnreadResponse,
        CasePriority priority,
        CaseProgressResponse progress,
        CaseReadinessResponse readiness,
        LocalDate dueDate,
        CaseTaskResponse currentTask,
        List<CaseTaskResponse> tasks,
        String workflowCatalogVersion,
        Map<String, Object> workflowSnapshot,
        Instant updatedAt
) {
    static CaseProjectionResponse from(CaseProjection projection) {
        return new CaseProjectionResponse(
                projection.caseId(),
                projection.workerId(),
                projection.workerDisplayName(),
                projection.title(),
                projection.lifecycleStatus(),
                projection.displayStatus(),
                projection.hasUnreadResponse(),
                projection.priority(),
                CaseProgressResponse.from(projection.progress()),
                CaseReadinessResponse.from(projection.readiness()),
                projection.dueDate(),
                CaseTaskResponse.from(projection.currentTask()),
                projection.tasks().stream().map(CaseTaskResponse::from).toList(),
                projection.workflowCatalogVersion(),
                projection.workflowSnapshot(),
                projection.updatedAt()
        );
    }
}
