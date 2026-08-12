package com.fowoco.server.casework.application;

import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.LocalDate;
import java.util.UUID;

public record CaseTaskProjection(
        UUID taskId,
        TaskType taskType,
        String title,
        TaskStatus status,
        LocalDate dueDate,
        UUID assigneeId,
        String assigneeDisplayName
) {
}
