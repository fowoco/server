package com.fowoco.server.reliability.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class EventPublication {

    private static final Pattern ERROR_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,79}$");

    private final UUID eventId;
    private final UUID companyId;
    private final String eventType;
    private final String payloadVersion;
    private final String aggregateType;
    private final UUID aggregateId;
    private final EventActorType actorType;
    private final UUID actorId;
    private final String requestId;
    private final String traceId;
    private final String payloadJson;
    private final Instant occurredAt;
    private final Instant createdAt;
    private EventPublicationStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String leaseOwner;
    private Instant leaseExpiresAt;
    private String lastErrorCode;
    private Instant completedAt;
    private Instant updatedAt;
    private long version;

    private EventPublication(
            UUID eventId,
            UUID companyId,
            String eventType,
            String payloadVersion,
            String aggregateType,
            UUID aggregateId,
            EventActorType actorType,
            UUID actorId,
            String requestId,
            String traceId,
            String payloadJson,
            EventPublicationStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            String leaseOwner,
            Instant leaseExpiresAt,
            String lastErrorCode,
            Instant completedAt,
            Instant occurredAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.eventId = Objects.requireNonNull(eventId);
        this.companyId = Objects.requireNonNull(companyId);
        this.eventType = requireText(eventType);
        this.payloadVersion = requireText(payloadVersion);
        this.aggregateType = requireText(aggregateType);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.actorType = Objects.requireNonNull(actorType);
        this.actorId = actorId;
        this.requestId = requireText(requestId);
        this.traceId = traceId;
        this.payloadJson = requireText(payloadJson);
        this.status = Objects.requireNonNull(status);
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.leaseOwner = leaseOwner;
        this.leaseExpiresAt = leaseExpiresAt;
        this.lastErrorCode = lastErrorCode;
        this.completedAt = completedAt;
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static EventPublication pending(
            DomainEventEnvelope event,
            String payloadJson,
            Instant publishedAt
    ) {
        Objects.requireNonNull(event);
        Objects.requireNonNull(publishedAt);
        if (publishedAt.isBefore(event.occurredAt())) {
            throw new IllegalArgumentException("publishedAt cannot precede occurredAt");
        }
        return new EventPublication(
                event.eventId(),
                event.companyId(),
                event.eventType(),
                event.payloadVersion(),
                event.aggregateType(),
                event.aggregateId(),
                event.actorType(),
                event.actorId(),
                event.requestId(),
                event.traceId(),
                payloadJson,
                EventPublicationStatus.PENDING,
                0,
                publishedAt,
                null,
                null,
                null,
                null,
                event.occurredAt(),
                publishedAt,
                publishedAt,
                0
        );
    }

    public static EventPublication restore(
            UUID eventId,
            UUID companyId,
            String eventType,
            String payloadVersion,
            String aggregateType,
            UUID aggregateId,
            EventActorType actorType,
            UUID actorId,
            String requestId,
            String traceId,
            String payloadJson,
            EventPublicationStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            String leaseOwner,
            Instant leaseExpiresAt,
            String lastErrorCode,
            Instant completedAt,
            Instant occurredAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new EventPublication(
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

    public boolean isClaimableAt(Instant now) {
        Objects.requireNonNull(now);
        return switch (status) {
            case PENDING, RETRY_WAIT ->
                    nextAttemptAt != null && !nextAttemptAt.isAfter(now);
            case PROCESSING ->
                    leaseExpiresAt != null && !leaseExpiresAt.isAfter(now);
            case COMPLETED, REVIEW_REQUIRED -> false;
        };
    }

    public void claim(String owner, Instant now, Duration leaseDuration) {
        String normalizedOwner = requireOwner(owner);
        Objects.requireNonNull(now);
        requirePositive(leaseDuration, "leaseDuration");
        if (!isClaimableAt(now)) {
            throw new IllegalStateException("Event publication is not claimable.");
        }
        status = EventPublicationStatus.PROCESSING;
        attemptCount++;
        nextAttemptAt = null;
        leaseOwner = normalizedOwner;
        leaseExpiresAt = now.plus(leaseDuration);
        lastErrorCode = null;
        completedAt = null;
        updatedAt = now;
    }

    public void complete(String owner, Instant now) {
        requireActiveLease(owner, now);
        status = EventPublicationStatus.COMPLETED;
        clearLease();
        nextAttemptAt = null;
        lastErrorCode = null;
        completedAt = now;
        updatedAt = now;
    }

    public void retry(String owner, String errorCode, Instant nextAttempt, Instant now) {
        requireActiveLease(owner, now);
        if (nextAttempt == null || !nextAttempt.isAfter(now)) {
            throw new IllegalArgumentException("nextAttempt must be after now");
        }
        status = EventPublicationStatus.RETRY_WAIT;
        clearLease();
        nextAttemptAt = nextAttempt;
        lastErrorCode = requireErrorCode(errorCode);
        completedAt = null;
        updatedAt = now;
    }

    public void requireReview(String owner, String errorCode, Instant now) {
        requireActiveLease(owner, now);
        status = EventPublicationStatus.REVIEW_REQUIRED;
        clearLease();
        nextAttemptAt = null;
        lastErrorCode = requireErrorCode(errorCode);
        completedAt = null;
        updatedAt = now;
    }

    public void requireActiveLease(String owner, Instant now) {
        String normalizedOwner = requireOwner(owner);
        Objects.requireNonNull(now);
        if (status != EventPublicationStatus.PROCESSING
                || !normalizedOwner.equals(leaseOwner)
                || leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException("Event publication lease is not active.");
        }
    }

    private void clearLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    private static String requireErrorCode(String errorCode) {
        if (errorCode == null || !ERROR_CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("Invalid safe event error code.");
        }
        return errorCode;
    }

    private static String requireOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("lease owner must be 1 to 128 characters");
        }
        return owner.trim();
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.trim();
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public DomainEventEnvelope toEnvelope(SafeEventPayload payload) {
        return new DomainEventEnvelope(
                eventId,
                eventType,
                payloadVersion,
                aggregateType,
                aggregateId,
                companyId,
                actorType,
                actorId,
                requestId,
                traceId,
                occurredAt,
                payload
        );
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String eventType() {
        return eventType;
    }

    public String payloadVersion() {
        return payloadVersion;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public UUID aggregateId() {
        return aggregateId;
    }

    public EventActorType actorType() {
        return actorType;
    }

    public UUID actorId() {
        return actorId;
    }

    public String requestId() {
        return requestId;
    }

    public String traceId() {
        return traceId;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public EventPublicationStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public String leaseOwner() {
        return leaseOwner;
    }

    public Instant leaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String lastErrorCode() {
        return lastErrorCode;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
