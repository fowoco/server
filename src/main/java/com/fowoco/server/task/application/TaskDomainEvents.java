package com.fowoco.server.task.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TaskDomainEvents {

    private static final String PAYLOAD_VERSION = "1";
    private static final String AGGREGATE_TYPE = "Task";
    private static final Set<String> TASK_CREATED_FIELDS = Set.of(
            "task_type",
            "status",
            "workflow_id",
            "workflow_catalog_version",
            "source"
    );
    private static final Set<String> TASK_CANCELLED_FIELDS = Set.of(
            "previous_status",
            "status"
    );

    private TaskDomainEvents() {
    }

    static DomainEventEnvelope taskCreated(
            UUID eventId,
            Task task,
            ActorContext actor,
            RequestMetadata metadata,
            Instant occurredAt
    ) {
        return envelope(
                eventId,
                "TaskCreated",
                task,
                actor,
                metadata,
                occurredAt,
                SafeEventPayload.of(
                        TASK_CREATED_FIELDS,
                        Map.of(
                                "task_type", task.taskType(),
                                "status", task.status(),
                                "workflow_id", task.workflowId(),
                                "workflow_catalog_version", task.workflowCatalogVersion(),
                                "source", task.source()
                        )
                )
        );
    }

    static DomainEventEnvelope taskCancelled(
            UUID eventId,
            Task task,
            TaskStatus previousStatus,
            ActorContext actor,
            RequestMetadata metadata,
            Instant occurredAt
    ) {
        return envelope(
                eventId,
                "TaskCancelled",
                task,
                actor,
                metadata,
                occurredAt,
                SafeEventPayload.of(
                        TASK_CANCELLED_FIELDS,
                        Map.of(
                                "previous_status", previousStatus,
                                "status", task.status()
                        )
                )
        );
    }

    private static DomainEventEnvelope envelope(
            UUID eventId,
            String eventType,
            Task task,
            ActorContext actor,
            RequestMetadata metadata,
            Instant occurredAt,
            SafeEventPayload payload
    ) {
        return new DomainEventEnvelope(
                eventId,
                eventType,
                PAYLOAD_VERSION,
                AGGREGATE_TYPE,
                task.taskId(),
                task.companyId(),
                EventActorType.HR_USER,
                actor.actorId(),
                metadata.requestId(),
                metadata.traceId(),
                occurredAt,
                payload
        );
    }
}
