package com.fowoco.server.approval.application;

import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record ApprovalResult(
        UUID approvalRequestId,
        UUID taskId,
        ApprovalStatus approvalStatus,
        TaskStatus taskStatus,
        long taskVersion,
        Instant requestedAt,
        Instant decidedAt
) {
}
