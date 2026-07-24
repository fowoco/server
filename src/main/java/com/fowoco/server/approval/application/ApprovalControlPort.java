package com.fowoco.server.approval.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.task.domain.Task;
import java.time.Instant;
import java.util.UUID;

public interface ApprovalControlPort {

    boolean hasValidApproval(
            UUID taskId,
            UUID companyId,
            long contentRevision,
            String criticalFingerprint
    );

    void invalidateForCriticalChange(
            UUID taskId,
            ActorContext actorContext,
            String reason,
            Instant occurredAt,
            RequestMetadata metadata
    );

    Task replaceReviewAfterCriticalChange(
            UUID taskId,
            ActorContext actorContext,
            String reason,
            Instant occurredAt,
            RequestMetadata metadata
    );
}
