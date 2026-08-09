package com.fowoco.server.approval.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import com.fowoco.server.task.domain.Task;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ApprovalDomainEvents {

    private static final String PAYLOAD_VERSION = "1";
    private static final String AGGREGATE_TYPE = "Task";
    private static final Set<String> APPROVAL_REQUESTED_FIELDS = Set.of(
            "task_title",
            "task_type"
    );

    private ApprovalDomainEvents() {
    }

    static DomainEventEnvelope approvalRequested(
            UUID eventId,
            Task task,
            ActorContext actor,
            RequestMetadata metadata,
            Instant occurredAt
    ) {
        return new DomainEventEnvelope(
                eventId,
                "ApprovalRequested",
                PAYLOAD_VERSION,
                AGGREGATE_TYPE,
                task.taskId(),
                task.companyId(),
                EventActorType.HR_USER,
                actor.actorId(),
                metadata.requestId(),
                metadata.traceId(),
                occurredAt,
                SafeEventPayload.of(
                        APPROVAL_REQUESTED_FIELDS,
                        Map.of(
                                "task_title", task.title(),
                                "task_type", task.taskType()
                        )
                )
        );
    }
}
