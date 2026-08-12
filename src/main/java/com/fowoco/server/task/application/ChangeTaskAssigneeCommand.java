package com.fowoco.server.task.application;

import java.util.Objects;
import java.util.UUID;

public record ChangeTaskAssigneeCommand(
        UUID assigneeId,
        long expectedVersion
) {
    public ChangeTaskAssigneeCommand {
        Objects.requireNonNull(assigneeId, "assigneeId must not be null");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
