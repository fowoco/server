package com.fowoco.server.workerlink.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class WorkerLink {

    private final UUID workerLinkId;
    private final UUID taskId;
    private final UUID companyId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final WorkerLinkStatus status;
    private final ConversationStatus conversationStatus;
    private final UUID assigneeId;
    private final UUID issuedBy;
    private final UUID replacesLinkId;
    private final String idempotencyKey;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public WorkerLink(
            UUID workerLinkId,
            UUID taskId,
            UUID companyId,
            String tokenHash,
            Instant expiresAt,
            WorkerLinkStatus status,
            ConversationStatus conversationStatus,
            UUID assigneeId,
            UUID issuedBy,
            UUID replacesLinkId,
            String idempotencyKey,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.workerLinkId = Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.tokenHash = requireText(tokenHash, "tokenHash");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.conversationStatus = Objects.requireNonNull(conversationStatus, "conversationStatus must not be null");
        this.assigneeId = assigneeId;
        this.issuedBy = Objects.requireNonNull(issuedBy, "issuedBy must not be null");
        this.replacesLinkId = replacesLinkId;
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public static WorkerLink issue(
            UUID workerLinkId,
            UUID taskId,
            UUID companyId,
            String tokenHash,
            Instant expiresAt,
            UUID issuedBy,
            UUID replacesLinkId,
            String idempotencyKey,
            Instant now
    ) {
        return new WorkerLink(
                workerLinkId,
                taskId,
                companyId,
                tokenHash,
                expiresAt,
                WorkerLinkStatus.ACTIVE,
                ConversationStatus.WAITING_WORKER,
                null,
                issuedBy,
                replacesLinkId,
                idempotencyKey,
                now,
                now,
                0L
        );
    }

    public WorkerLink revoke(Instant now) {
        return new WorkerLink(
                workerLinkId, taskId, companyId, tokenHash, expiresAt,
                WorkerLinkStatus.REVOKED, conversationStatus, assigneeId, issuedBy,
                replacesLinkId, idempotencyKey, createdAt, now, version
        );
    }

    public WorkerLink markNeedsFollowup(Instant now) {
        return new WorkerLink(
                workerLinkId, taskId, companyId, tokenHash, expiresAt,
                status, ConversationStatus.NEEDS_FOLLOWUP, assigneeId, issuedBy,
                replacesLinkId, idempotencyKey, createdAt, now, version
        );
    }

    public WorkerLink markReviewed(Instant now) {
        if (conversationStatus != ConversationStatus.NEEDS_FOLLOWUP) {
            return this;
        }
        return new WorkerLink(
                workerLinkId, taskId, companyId, tokenHash, expiresAt,
                status, ConversationStatus.REOPENED, assigneeId, issuedBy,
                replacesLinkId, idempotencyKey, createdAt, now, version
        );
    }

    public boolean isUsable(Instant now) {
        return status == WorkerLinkStatus.ACTIVE && expiresAt.isAfter(now);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public UUID workerLinkId() {
        return workerLinkId;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public WorkerLinkStatus status() {
        return status;
    }

    public ConversationStatus conversationStatus() {
        return conversationStatus;
    }

    public UUID assigneeId() {
        return assigneeId;
    }

    public UUID issuedBy() {
        return issuedBy;
    }

    public UUID replacesLinkId() {
        return replacesLinkId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
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
