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
    private final WorkerLinkDeliveryStatus deliveryStatus;
    private final Instant sentAt;
    private final UUID sentBy;
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
            WorkerLinkDeliveryStatus deliveryStatus,
            Instant sentAt,
            UUID sentBy,
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
        this.deliveryStatus = Objects.requireNonNull(deliveryStatus, "deliveryStatus must not be null");
        validateDelivery(deliveryStatus, sentAt, sentBy, createdAt);
        this.sentAt = sentAt;
        this.sentBy = sentBy;
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
                WorkerLinkDeliveryStatus.NOT_SENT,
                null,
                null,
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
                WorkerLinkStatus.REVOKED, conversationStatus, deliveryStatus, sentAt, sentBy,
                assigneeId, issuedBy,
                replacesLinkId, idempotencyKey, createdAt, now, version
        );
    }

    public WorkerLink markNeedsFollowup(Instant now) {
        return new WorkerLink(
                workerLinkId, taskId, companyId, tokenHash, expiresAt,
                status, ConversationStatus.NEEDS_FOLLOWUP, deliveryStatus, sentAt, sentBy,
                assigneeId, issuedBy,
                replacesLinkId, idempotencyKey, createdAt, now, version
        );
    }

    public WorkerLink markReviewed(Instant now) {
        if (conversationStatus != ConversationStatus.NEEDS_FOLLOWUP) {
            return this;
        }
        return new WorkerLink(
                workerLinkId, taskId, companyId, tokenHash, expiresAt,
                status, ConversationStatus.REOPENED, deliveryStatus, sentAt, sentBy,
                assigneeId, issuedBy,
                replacesLinkId, idempotencyKey, createdAt, now, version
        );
    }

    public WorkerLink markSent(UUID actorId, Instant now) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (deliveryStatus == WorkerLinkDeliveryStatus.SENT) {
            return this;
        }
        return new WorkerLink(
                workerLinkId, taskId, companyId, tokenHash, expiresAt,
                status, conversationStatus, WorkerLinkDeliveryStatus.SENT, now, actorId,
                assigneeId, issuedBy, replacesLinkId, idempotencyKey, createdAt, now, version
        );
    }

    public WorkerLink markSending(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (deliveryStatus != WorkerLinkDeliveryStatus.NOT_SENT) {
            throw new IllegalStateException("only a not-sent worker link can start SMS delivery");
        }
        return withDeliveryState(WorkerLinkDeliveryStatus.SENDING, null, null, now);
    }

    public WorkerLink markDeliveryReviewRequired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (deliveryStatus == WorkerLinkDeliveryStatus.REVIEW_REQUIRED) {
            return this;
        }
        if (deliveryStatus != WorkerLinkDeliveryStatus.SENDING) {
            throw new IllegalStateException("only an in-flight SMS delivery can require review");
        }
        return withDeliveryState(WorkerLinkDeliveryStatus.REVIEW_REQUIRED, null, null, now);
    }

    public WorkerLink markNotSentAfterRejectedDelivery(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (deliveryStatus == WorkerLinkDeliveryStatus.NOT_SENT) {
            return this;
        }
        if (deliveryStatus != WorkerLinkDeliveryStatus.SENDING) {
            throw new IllegalStateException("only an in-flight SMS delivery can return to not-sent");
        }
        return withDeliveryState(WorkerLinkDeliveryStatus.NOT_SENT, null, null, now);
    }

    private WorkerLink withDeliveryState(
            WorkerLinkDeliveryStatus nextStatus,
            Instant nextSentAt,
            UUID nextSentBy,
            Instant now
    ) {
        return new WorkerLink(
                workerLinkId, taskId, companyId, tokenHash, expiresAt,
                status, conversationStatus, nextStatus, nextSentAt, nextSentBy,
                assigneeId, issuedBy, replacesLinkId, idempotencyKey, createdAt, now, version
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

    private static void validateDelivery(
            WorkerLinkDeliveryStatus deliveryStatus,
            Instant sentAt,
            UUID sentBy,
            Instant createdAt
    ) {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        boolean sent = deliveryStatus == WorkerLinkDeliveryStatus.SENT;
        if (sent != (sentAt != null && sentBy != null)) {
            throw new IllegalArgumentException("sentAt and sentBy must match deliveryStatus");
        }
        if (sentAt != null && sentAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("sentAt must not be before createdAt");
        }
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

    public WorkerLinkDeliveryStatus deliveryStatus() {
        return deliveryStatus;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public UUID sentBy() {
        return sentBy;
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
