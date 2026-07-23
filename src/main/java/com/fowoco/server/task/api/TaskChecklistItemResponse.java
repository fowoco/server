package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.domain.TaskChecklistItem;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskChecklistItemResponse(
        UUID checklistItemId,
        String itemCode,
        String label,
        boolean required,
        boolean completed,
        UUID completedBy,
        Instant completedAt,
        long version
) {
    static TaskChecklistItemResponse from(TaskChecklistItem item) {
        return new TaskChecklistItemResponse(
                item.checklistItemId(),
                item.itemCode(),
                item.label(),
                item.required(),
                item.completed(),
                item.completedBy(),
                item.completedAt(),
                item.version()
        );
    }
}
