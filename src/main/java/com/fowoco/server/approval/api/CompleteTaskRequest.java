package com.fowoco.server.approval.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.approval.application.CompleteTaskCommand;
import jakarta.validation.constraints.PositiveOrZero;

public record CompleteTaskRequest(
        @JsonProperty("expected_version") @PositiveOrZero long expectedVersion
) {

    public CompleteTaskCommand toCommand() {
        return new CompleteTaskCommand(expectedVersion);
    }
}
