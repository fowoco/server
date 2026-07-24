package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.CancelTaskCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CancelTaskRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {
    CancelTaskCommand toCommand() {
        return new CancelTaskCommand(expectedVersion, reason);
    }
}
