package com.fowoco.server.workerlink.application;

import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import com.fowoco.server.task.domain.Task;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkerResponseDomainEvents {

    public static final String SLOT_ANSWERS_SUBMITTED = "WorkerSlotAnswersSubmitted";
    public static final String DOCUMENT_SUBMITTED = "WorkerResponseSubmitted";
    private static final String PAYLOAD_VERSION = "1";
    private static final String AGGREGATE_TYPE = "Task";
    private static final Set<String> RESPONSE_SUBMITTED_FIELDS = Set.of(
            "task_title",
            "task_type"
    );

    private WorkerResponseDomainEvents() {
    }

    static DomainEventEnvelope submitted(
            UUID eventId,
            UUID responseId,
            Task task,
            UUID companyId,
            UUID delegatedActorId,
            String eventType,
            Instant occurredAt
    ) {
        return new DomainEventEnvelope(
                eventId,
                eventType,
                PAYLOAD_VERSION,
                AGGREGATE_TYPE,
                task.taskId(),
                companyId,
                EventActorType.WORKER_LINK,
                delegatedActorId,
                responseId.toString(),
                null,
                occurredAt,
                SafeEventPayload.of(
                        RESPONSE_SUBMITTED_FIELDS,
                        Map.of(
                                "task_title", task.title(),
                                "task_type", task.taskType()
                        )
                )
        );
    }
}
