package com.fowoco.server.reliability.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventConsumption(
        UUID consumptionId,
        UUID eventId,
        UUID companyId,
        String handlerName,
        Instant completedAt
) {

    public EventConsumption {
        Objects.requireNonNull(consumptionId, "consumptionId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        if (handlerName == null || handlerName.isBlank() || handlerName.length() > 120) {
            throw new IllegalArgumentException("handlerName must be 1 to 120 characters");
        }
        handlerName = handlerName.trim();
        Objects.requireNonNull(completedAt, "completedAt must not be null");
    }
}
