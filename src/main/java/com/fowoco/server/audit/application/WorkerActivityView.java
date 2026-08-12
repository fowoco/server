package com.fowoco.server.audit.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkerActivityView(
        UUID activityId,
        WorkerActivityType type,
        UUID taskId,
        String taskTitle,
        String summary,
        Instant occurredAt
) {
    public WorkerActivityView {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        if (taskTitle == null || taskTitle.isBlank()) {
            throw new IllegalArgumentException("taskTitle must not be blank");
        }
        taskTitle = taskTitle.strip();
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.strip();
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
