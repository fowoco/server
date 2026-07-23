package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.CreateTaskCommand;
import com.fowoco.server.task.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateTaskRequest(
        @NotNull UUID workerId,
        UUID caseId,
        @NotNull TaskType taskType,
        @NotBlank @Size(max = 100) String workflowId,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        LocalDate dueDate,
        Map<String, Object> businessData
) {
    CreateTaskCommand toCommand() {
        return new CreateTaskCommand(
                workerId,
                caseId,
                taskType,
                workflowId,
                title,
                description,
                dueDate,
                businessData
        );
    }
}
