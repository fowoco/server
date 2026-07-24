package com.fowoco.server.approval.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.approval.application.ApprovalResult;
import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        @JsonProperty("approval_request_id") UUID approvalRequestId,
        @JsonProperty("task_id") UUID taskId,
        @JsonProperty("approval_status") ApprovalStatus approvalStatus,
        @JsonProperty("task_status") TaskStatus taskStatus,
        @JsonProperty("content_revision") long contentRevision,
        @JsonProperty("task_version") long taskVersion,
        @JsonProperty("requested_at") Instant requestedAt,
        @JsonProperty("decided_at") Instant decidedAt
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
