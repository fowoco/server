package com.fowoco.server.task.application;

import com.fowoco.server.task.domain.TaskType;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record CreateTaskCommand(
        UUID workerId,
        UUID caseId,
        TaskType taskType,
        String workflowId,
        String title,
        String description,
        LocalDate dueDate,
        Map<String, Object> businessData
) {
}
