package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.ApprovalResult;
import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        UUID approvalRequestId,
        UUID taskId,
        ApprovalStatus approvalStatus,
        TaskStatus taskStatus,
        long contentRevision,
        long taskVersion,
        Instant requestedAt,
        Instant decidedAt
) {

    public static ApprovalResponse from(ApprovalResult result) {
        return new ApprovalResponse(
                result.approvalRequestId(),
                result.taskId(),
                result.approvalStatus(),
                result.taskStatus(),
                result.contentRevision(),
                result.taskVersion(),
                result.requestedAt(),
                result.decidedAt()
        );
    }
}
