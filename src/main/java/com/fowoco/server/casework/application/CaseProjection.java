package com.fowoco.server.casework.application;

import com.fowoco.server.casework.domain.CaseDisplayStatus;
import com.fowoco.server.casework.domain.CaseLifecycleStatus;
import com.fowoco.server.casework.domain.CasePriority;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CaseProjection(
        UUID caseId,
        UUID workerId,
        String workerDisplayName,
        String title,
        CaseLifecycleStatus lifecycleStatus,
        CaseDisplayStatus displayStatus,
        boolean hasUnreadResponse,
        CasePriority priority,
        CaseProgress progress,
        CaseReadiness readiness,
        LocalDate dueDate,
        CaseTaskProjection currentTask,
        List<CaseTaskProjection> tasks,
        String workflowCatalogVersion,
        Map<String, Object> workflowSnapshot,
        Instant updatedAt
) {
}
