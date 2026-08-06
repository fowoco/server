package com.fowoco.server.reliability.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxManualRetry(
        UUID manualRetryId,
        UUID companyId,
        UUID eventId,
        String idempotencyKeyHash,
        String requestHash,
        String reason,
        UUID requestedBy,
        String requestId,
        String traceId,
        EventPublicationStatus acceptedStatus,
        long acceptedVersion,
        Instant createdAt
) {
    public OutboxManualRetry {
        Objects.requireNonNull(manualRetryId, "manualRetryId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        Objects.requireNonNull(acceptedStatus, "acceptedStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireLength(idempotencyKeyHash, 64, "idempotencyKeyHash");
        requireLength(requestHash, 64, "requestHash");
        requireRange(reason, 10, 300, "reason");
        requireRange(requestId, 1, 128, "requestId");
        if (traceId != null) {
            requireLength(traceId, 32, "traceId");
        }
        if (acceptedVersion < 0) {
            throw new IllegalArgumentException("acceptedVersion must not be negative");
        }
    }

    private static void requireLength(String value, int length, String name) {
        if (value == null || value.length() != length) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
    }

    private static void requireRange(String value, int minimum, int maximum, String name) {
        if (value == null || value.isBlank()
                || value.length() < minimum || value.length() > maximum) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
    }
}
