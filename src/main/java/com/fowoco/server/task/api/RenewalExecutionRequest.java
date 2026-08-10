package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.renewal.RenewalExecutionCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RenewalExecutionRequest(
        @NotBlank @Size(max = 10_000) String instruction,
        @NotNull @Min(0) Long expectedVersion
) {
    RenewalExecutionCommand toCommand() {
        return new RenewalExecutionCommand(instruction, expectedVersion);
    }
}
