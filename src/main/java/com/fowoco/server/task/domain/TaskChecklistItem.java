package com.fowoco.server.task.domain;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TaskChecklistItem {

    private final UUID checklistItemId;
    private final UUID taskId;
    private final UUID companyId;
    private final String itemCode;
    private final String label;
    private final boolean required;
    private boolean completed;
    private UUID completedBy;
    private Instant completedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public TaskChecklistItem(
            UUID checklistItemId,
            UUID taskId,
            UUID companyId,
            String itemCode,
            String label,
            boolean required,
            boolean completed,
            UUID completedBy,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.checklistItemId = Objects.requireNonNull(checklistItemId);
        this.taskId = Objects.requireNonNull(taskId);
        this.companyId = Objects.requireNonNull(companyId);
        this.itemCode = requireText(itemCode);
        this.label = requireText(label);
        this.required = required;
        this.completed = completed;
        this.completedBy = completedBy;
        this.completedAt = completedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
        validateCompletion();
    }

    public static TaskChecklistItem create(
            UUID checklistItemId,
            UUID taskId,
            UUID companyId,
            String itemCode,
            String label,
            boolean required,
            Instant now
    ) {
        return new TaskChecklistItem(
                checklistItemId,
                taskId,
                companyId,
                itemCode,
                label,
                required,
                false,
                null,
                null,
                now,
                now,
                0
        );
    }

    public boolean updateCompletion(
            boolean nextCompleted,
            long expectedVersion,
            UUID actorId,
            Instant now
    ) {
        if (version != expectedVersion) {
            throw new ApiException(TaskErrorCode.CONCURRENT_MODIFICATION);
        }
        if (completed == nextCompleted) {
            return false;
        }
        completed = nextCompleted;
        completedBy = nextCompleted ? Objects.requireNonNull(actorId) : null;
        completedAt = nextCompleted ? Objects.requireNonNull(now) : null;
        updatedAt = Objects.requireNonNull(now);
        return true;
    }

    private void validateCompletion() {
        if (completed && (completedBy == null || completedAt == null)) {
            throw new IllegalArgumentException("completed checklist needs actor and time");
        }
        if (!completed && (completedBy != null || completedAt != null)) {
            throw new IllegalArgumentException("checklist completion actor and time must match status");
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.trim();
    }

    public UUID checklistItemId() {
        return checklistItemId;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String itemCode() {
        return itemCode;
    }

    public String label() {
        return label;
    }

    public boolean required() {
        return required;
    }

    public boolean completed() {
        return completed;
    }

    public UUID completedBy() {
        return completedBy;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
