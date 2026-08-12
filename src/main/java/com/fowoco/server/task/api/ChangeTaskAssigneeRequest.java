package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.ChangeTaskAssigneeCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChangeTaskAssigneeRequest(
        @Schema(
                description = "같은 사업장의 활성 ADMIN 또는 HR 사용자 ID",
                example = "7e2722bb-3c72-4aa0-b37c-28931c4f8e53"
        )
        @NotNull UUID assigneeId,
        @Schema(description = "조회한 업무카드의 현재 version", example = "3")
        @NotNull @Min(0) Long expectedVersion
) {
    ChangeTaskAssigneeCommand toCommand() {
        return new ChangeTaskAssigneeCommand(assigneeId, expectedVersion);
    }
}
