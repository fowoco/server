package com.fowoco.server.workerlink.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class WorkerResponse {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final UUID responseId;
    private final UUID workerLinkId;
    private final UUID companyId;
    private final WorkerResponseType responseType;
    private final String message;
    private final String idempotencyKey;
    private final Instant receivedAt;

    public WorkerResponse(
            UUID responseId,
            UUID workerLinkId,
            UUID companyId,
            WorkerResponseType responseType,
            String message,
            String idempotencyKey,
            Instant receivedAt
    ) {
        this.responseId = Objects.requireNonNull(responseId, "responseId must not be null");
        this.workerLinkId = Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.responseType = Objects.requireNonNull(responseType, "responseType must not be null");
        this.message = normalizeMessage(message);
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public static WorkerResponse create(
            UUID responseId,
            UUID workerLinkId,
            UUID companyId,
            WorkerResponseType responseType,
            String message,
            String idempotencyKey,
            Instant now
    ) {
        return new WorkerResponse(responseId, workerLinkId, companyId, responseType, message, idempotencyKey, now);
    }

    private static String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public UUID responseId() {
        return responseId;
    }

    public UUID workerLinkId() {
        return workerLinkId;
    }

    public UUID companyId() {
        return companyId;
    }

    public WorkerResponseType responseType() {
        return responseType;
    }

    public String message() {
        return message;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
