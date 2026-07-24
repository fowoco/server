package com.fowoco.server.reliability.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        String payloadVersion,
        String aggregateType,
        UUID aggregateId,
        UUID companyId,
        EventActorType actorType,
        UUID actorId,
        String requestId,
        String traceId,
        Instant occurredAt,
        SafeEventPayload payload
) {

    private static final Pattern TYPE_NAME = Pattern.compile("^[A-Z][A-Za-z0-9]{1,99}$");
    private static final Pattern PAYLOAD_VERSION = Pattern.compile("^[1-9][0-9]{0,9}$");
    private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-f]{32}$");

    public DomainEventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireType(eventType, "eventType");
        if (payloadVersion == null || !PAYLOAD_VERSION.matcher(payloadVersion).matches()) {
            throw new IllegalArgumentException("payloadVersion must be a positive integer string");
        }
        requireType(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(actorType, "actorType must not be null");
        if (actorType != EventActorType.SYSTEM_RULE && actorId == null) {
            throw new IllegalArgumentException("actorId is required for non-system events");
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must be 1 to 128 characters");
        }
        requestId = requestId.trim();
        if (traceId != null && !TRACE_ID.matcher(traceId).matches()) {
            throw new IllegalArgumentException("traceId must be a 32-character lowercase hex value");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    private static void requireType(String value, String fieldName) {
        if (value == null || !TYPE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must use a stable type name");
        }
    }
}
