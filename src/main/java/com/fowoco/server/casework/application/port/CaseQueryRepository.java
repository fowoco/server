package com.fowoco.server.casework.application.port;

import com.fowoco.server.casework.application.CaseSearchQuery;
import com.fowoco.server.casework.domain.CaseLifecycleStatus;
import com.fowoco.server.casework.domain.CasePriority;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseQueryRepository {

    CaseRecordPage findPage(UUID companyId, CaseSearchQuery query);

    Optional<CaseRecord> findById(UUID companyId, UUID caseId);

    List<CaseTaskRecord> findTasks(UUID companyId, List<UUID> caseIds);

    record CaseRecordPage(List<CaseRecord> items, long totalElements) {
    }

    record CaseRecord(
            UUID caseId,
            UUID workerId,
            String workerDisplayName,
            String title,
            CaseLifecycleStatus lifecycleStatus,
            CasePriority priority,
            String workflowCatalogVersion,
            String workflowSnapshotJson,
            boolean linkSent,
            boolean reviewRequired,
            boolean unreadResponse,
            int completedChecklistItems,
            int totalChecklistItems,
            int verifiedDocuments,
            int totalDocuments,
            int pendingApprovals,
            int approvedApprovals,
            int workerResponses,
            int evidenceItems,
            Instant updatedAt
    ) {
    }

    record CaseTaskRecord(
            UUID caseId,
            UUID taskId,
            TaskType taskType,
            String title,
            TaskStatus status,
            LocalDate dueDate,
            UUID assigneeId,
            String assigneeDisplayName
    ) {
    }
}
