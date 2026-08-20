package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.TaskResult;
import com.fowoco.server.task.application.action.TaskActionDecision;
import com.fowoco.server.task.application.action.TaskAvailableAction;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskTargetType;
import com.fowoco.server.task.domain.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskDetailResponse(
        UUID taskId,
        TaskTargetType targetType,
        UUID workerId,
        UUID caseId,
        TaskType taskType,
        String workflowId,
        String workflowCatalogVersion,
        String title,
        String description,
        Map<String, Object> businessData,
        TaskSource source,
        TaskStatus status,
        LocalDate dueDate,
        TaskAssigneeResponse assignee,
        long contentRevision,
        long version,
        List<String> missingRequiredSlots,
        List<TaskChecklistItemResponse> checklistItems,
        TaskAvailableAction nextAction,
        List<TaskAvailableAction> availableActions,
        String blockedReason,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt
) {
    static TaskDetailResponse from(TaskResult result, TaskActionDecision actionDecision) {
        Task task = result.task();
        return new TaskDetailResponse(
                task.taskId(),
                task.targetType(),
                task.workerId(),
                task.caseId(),
                task.taskType(),
                task.workflowId(),
                task.workflowCatalogVersion(),
                task.title(),
                task.description(),
                result.businessData(),
                task.source(),
                task.status(),
                task.dueDate(),
                TaskAssigneeResponse.from(result.assignee()),
                task.contentRevision(),
                task.version(),
                result.missingRequiredSlots(),
                result.checklistItems().stream().map(TaskChecklistItemResponse::from).toList(),
                actionDecision.nextAction(),
                actionDecision.availableActions(),
                actionDecision.blockedReason(),
                task.createdBy(),
                task.updatedBy(),
                task.createdAt(),
                task.updatedAt()
        );
    }
}
