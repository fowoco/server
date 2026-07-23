package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.UpdateTaskCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateTaskRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        LocalDate dueDate,
        @NotNull Map<String, Object> businessData,
        @NotNull @Min(0) Long expectedVersion
) {
    UpdateTaskCommand toCommand() {
        return new UpdateTaskCommand(title, description, dueDate, businessData, expectedVersion);
    }
}
