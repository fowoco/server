package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.EventPublication;
import com.fowoco.server.reliability.domain.EventPublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_publication")
class EventPublicationJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "event_type", nullable = false, length = 100, updatable = false)
    private String eventType;
    @Column(name = "payload_version", nullable = false, length = 20, updatable = false)
    private String payloadVersion;
    @Column(name = "aggregate_type", nullable = false, length = 60, updatable = false)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30, updatable = false)
    private EventActorType actorType;
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
    @Column(name = "request_id", nullable = false, length = 128, updatable = false)
    private String requestId;
    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payloadJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventPublicationStatus status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;
    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;
    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected EventPublicationJpaEntity() {
    }

    EventPublicationJpaEntity(EventPublication publication) {
        eventId = publication.eventId();
        companyId = publication.companyId();
        eventType = publication.eventType();
        payloadVersion = publication.payloadVersion();
        aggregateType = publication.aggregateType();
        aggregateId = publication.aggregateId();
        actorType = publication.actorType();
        actorId = publication.actorId();
        requestId = publication.requestId();
        traceId = publication.traceId();
        payloadJson = publication.payloadJson();
        occurredAt = publication.occurredAt();
        createdAt = publication.createdAt();
        version = publication.version();
        apply(publication);
    }

    void apply(EventPublication publication) {
        status = publication.status();
        attemptCount = publication.attemptCount();
        nextAttemptAt = publication.nextAttemptAt();
        leaseOwner = publication.leaseOwner();
        leaseExpiresAt = publication.leaseExpiresAt();
        lastErrorCode = publication.lastErrorCode();
        completedAt = publication.completedAt();
        updatedAt = publication.updatedAt();
    }

    EventPublication toDomain() {
        return EventPublication.restore(
                eventId,
                companyId,
                eventType,
                payloadVersion,
                aggregateType,
                aggregateId,
                actorType,
                actorId,
                requestId,
                traceId,
                payloadJson,
                status,
                attemptCount,
                nextAttemptAt,
                leaseOwner,
                leaseExpiresAt,
                lastErrorCode,
                completedAt,
                occurredAt,
                createdAt,
                updatedAt,
                version
        );
    }
}
