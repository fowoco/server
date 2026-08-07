package com.fowoco.server.document.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.domain.DocumentOcrRun;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import java.time.Instant;
import java.util.UUID;

final class DocumentOcrDomainEvents {

    static final String EXECUTION_REQUESTED = "DocumentOcrExecutionRequested";
    private static final String PAYLOAD_VERSION = "1";
    private static final String AGGREGATE_TYPE = "DocumentOcrRun";

    private DocumentOcrDomainEvents() {
    }

    static DomainEventEnvelope executionRequested(
            UUID eventId,
            DocumentOcrRun run,
            ActorContext actor,
            RequestMetadata metadata,
            Instant occurredAt
    ) {
        return new DomainEventEnvelope(
                eventId,
                EXECUTION_REQUESTED,
                PAYLOAD_VERSION,
                AGGREGATE_TYPE,
                run.ocrRunId(),
                run.companyId(),
                EventActorType.HR_USER,
                actor.actorId(),
                metadata.requestId(),
                metadata.traceId(),
                occurredAt,
                SafeEventPayload.empty()
        );
    }
}
