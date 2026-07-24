package com.fowoco.server.approval.application;

import com.fowoco.server.task.domain.TaskStatus;
import java.util.UUID;

public record TaskActionResult(UUID resourceId, UUID taskId, TaskStatus taskStatus, long taskVersion) {
}
