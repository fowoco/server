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
    private final String answersJson;
    private final String idempotencyKey;
    private final String requestFingerprint;
    private final Instant receivedAt;

    public WorkerResponse(
            UUID responseId,
            UUID workerLinkId,
            UUID companyId,
            WorkerResponseType responseType,
            String message,
            String answersJson,
            String idempotencyKey,
            String requestFingerprint,
            Instant receivedAt
    ) {
        this.responseId = Objects.requireNonNull(responseId, "responseId must not be null");
        this.workerLinkId = Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.responseType = Objects.requireNonNull(responseType, "responseType must not be null");
        this.message = normalizeMessage(message);
        this.answersJson = requireText(answersJson, "answersJson");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.requestFingerprint = normalizeFingerprint(requestFingerprint);
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    public static WorkerResponse create(
            UUID responseId,
            UUID workerLinkId,
            UUID companyId,
            WorkerResponseType responseType,
            String message,
            String answersJson,
            String idempotencyKey,
            String requestFingerprint,
            Instant now
    ) {
        return new WorkerResponse(
                responseId,
                workerLinkId,
                companyId,
                responseType,
                message,
                answersJson,
                idempotencyKey,
                requestFingerprint,
                now
        );
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

    private static String normalizeFingerprint(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestFingerprint must be a SHA-256 hex string");
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

    public String answersJson() {
        return answersJson;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
