package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.TaskActionResult;
import com.fowoco.server.task.domain.TaskStatus;
import java.util.UUID;

public record TaskActionResponse(
        UUID resourceId,
        UUID taskId,
        TaskStatus taskStatus,
        long taskVersion
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
