package com.fowoco.server.task.application;

import java.util.Objects;
import java.util.UUID;

public record TaskAssigneeView(
        UUID userId,
        String displayName
) {
    public TaskAssigneeView {
        Objects.requireNonNull(userId, "userId must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        displayName = displayName.strip();
    }
}
