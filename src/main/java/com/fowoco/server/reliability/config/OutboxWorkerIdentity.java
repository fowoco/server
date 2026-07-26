package com.fowoco.server.reliability.config;

public record OutboxWorkerIdentity(String value) {

    public OutboxWorkerIdentity {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Outbox worker identity must be 1 to 128 characters.");
        }
        value = value.trim();
    }
}
