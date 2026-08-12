package com.fowoco.server.casework.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.casework.application.CaseTaskProjection;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.LocalDate;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CaseTaskResponse(
        UUID taskId,
        TaskType taskType,
        String title,
        TaskStatus status,
        LocalDate dueDate
) {
    static CaseTaskResponse from(CaseTaskProjection task) {
        return task == null ? null : new CaseTaskResponse(
                task.taskId(),
                task.taskType(),
                task.title(),
                task.status(),
                task.dueDate()
        );
    }
}
