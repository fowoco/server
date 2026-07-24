package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.UpdateChecklistItemCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateChecklistItemRequest(
        @NotNull Boolean completed,
        @NotNull @Min(0) Long expectedVersion,
        @NotNull @Min(0) Long expectedTaskVersion
) {
    UpdateChecklistItemCommand toCommand() {
        return new UpdateChecklistItemCommand(
                completed,
                expectedVersion,
                expectedTaskVersion
        );
    }
}
