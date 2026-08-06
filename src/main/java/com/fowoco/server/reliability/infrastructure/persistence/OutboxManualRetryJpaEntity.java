package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.domain.EventPublicationStatus;
import com.fowoco.server.reliability.domain.OutboxManualRetry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_manual_retry")
class OutboxManualRetryJpaEntity {

    @Id
    @Column(name = "manual_retry_id", nullable = false, updatable = false)
    private UUID manualRetryId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "idempotency_key_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyKeyHash;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(name = "reason", nullable = false, length = 300, updatable = false)
    private String reason;
    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;
    @Column(name = "request_id", nullable = false, length = 128, updatable = false)
    private String requestId;
    @Column(name = "trace_id", length = 32, updatable = false)
    private String traceId;
    @Column(name = "previous_attempt_count", nullable = false, updatable = false)
    private int previousAttemptCount;
    @Enumerated(EnumType.STRING)
    @Column(name = "accepted_status", nullable = false, length = 30, updatable = false)
    private EventPublicationStatus acceptedStatus;
    @Column(name = "accepted_version", nullable = false, updatable = false)
    private long acceptedVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxManualRetryJpaEntity() {
    }

    OutboxManualRetryJpaEntity(OutboxManualRetry retry) {
        manualRetryId = retry.manualRetryId();
        companyId = retry.companyId();
        eventId = retry.eventId();
        idempotencyKeyHash = retry.idempotencyKeyHash();
        requestHash = retry.requestHash();
        reason = retry.reason();
        requestedBy = retry.requestedBy();
        requestId = retry.requestId();
        traceId = retry.traceId();
        previousAttemptCount = retry.previousAttemptCount();
        acceptedStatus = retry.acceptedStatus();
        acceptedVersion = retry.acceptedVersion();
        createdAt = retry.createdAt();
    }

    OutboxManualRetry toDomain() {
        return new OutboxManualRetry(
                manualRetryId,
                companyId,
                eventId,
                idempotencyKeyHash,
                requestHash,
                reason,
                requestedBy,
                requestId,
                traceId,
                previousAttemptCount,
                acceptedStatus,
                acceptedVersion,
                createdAt
        );
    }
}
