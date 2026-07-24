package com.fowoco.server.approval.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.approval.application.TaskActionResult;
import com.fowoco.server.task.domain.TaskStatus;
import java.util.UUID;

public record TaskActionResponse(
        @JsonProperty("resource_id") UUID resourceId,
        @JsonProperty("task_id") UUID taskId,
        @JsonProperty("task_status") TaskStatus taskStatus,
        @JsonProperty("task_version") long taskVersion
) {

    public static TaskActionResponse from(TaskActionResult result) {
        return new TaskActionResponse(
                result.resourceId(),
                result.taskId(),
                result.taskStatus(),
                result.taskVersion()
        );
    }
}
