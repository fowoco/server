package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.CompleteTaskCommand;
import jakarta.validation.constraints.PositiveOrZero;

public record CompleteTaskRequest(@PositiveOrZero long expectedVersion) {

    public CompleteTaskCommand toCommand() {
        return new CompleteTaskCommand(expectedVersion);
    }
}
