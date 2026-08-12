package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.TaskAssigneeView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskAssigneeResponse(
        @Schema(description = "담당자 사용자 ID") UUID userId,
        @Schema(description = "화면에 표시할 담당자 이름") String displayName
) {
    public static TaskAssigneeResponse from(TaskAssigneeView assignee) {
        return new TaskAssigneeResponse(assignee.userId(), assignee.displayName());
    }
}
