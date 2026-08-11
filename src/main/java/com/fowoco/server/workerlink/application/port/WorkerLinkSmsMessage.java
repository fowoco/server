package com.fowoco.server.workerlink.application.port;

import java.util.Objects;

public record WorkerLinkSmsMessage(
        String recipientPhone,
        String content
) {
    public WorkerLinkSmsMessage {
        recipientPhone = requireText(recipientPhone, "recipientPhone");
        content = requireText(content, "content");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
