package com.fowoco.server.workerlink.application;

import java.util.Objects;
import java.util.UUID;

public record WorkerLinkSmsDeliveryCommand(
        UUID workerLinkId,
        String recipientPhone,
        String rawToken,
        String idempotencyKey
) {
    public WorkerLinkSmsDeliveryCommand {
        Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        recipientPhone = requireText(recipientPhone, "recipientPhone");
        rawToken = requireText(rawToken, "rawToken");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
