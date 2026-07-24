package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskSummaryResponse(
        UUID taskId,
        UUID workerId,
        UUID caseId,
        TaskType taskType,
        String workflowId,
        String workflowCatalogVersion,
        String title,
        TaskSource source,
        TaskStatus status,
        LocalDate dueDate,
        long contentRevision,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    static TaskSummaryResponse from(Task task) {
        return new TaskSummaryResponse(
                task.taskId(),
                task.workerId(),
                task.caseId(),
                task.taskType(),
                task.workflowId(),
                task.workflowCatalogVersion(),
                task.title(),
                task.source(),
                task.status(),
                task.dueDate(),
                task.contentRevision(),
                task.version(),
                task.createdAt(),
                task.updatedAt()
        );
    }
}
