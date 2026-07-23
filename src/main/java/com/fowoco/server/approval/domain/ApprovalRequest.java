package com.fowoco.server.approval.domain;

import com.fowoco.server.approval.application.error.ApprovalErrorCode;
import com.fowoco.server.common.error.ApiException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ApprovalRequest {

    private final UUID approvalRequestId;
    private final UUID taskId;
    private final UUID companyId;
    private final long targetTaskVersion;
    private final long targetContentRevision;
    private Long approvedTaskVersion;
    private final String targetFingerprint;
    private ApprovalStatus status;
    private final String aiSnapshotJson;
    private final String hrSnapshotJson;
    private final String changedFieldsJson;
    private final String sourceVersionsJson;
    private final UUID requestedBy;
    private final Instant requestedAt;
    private UUID decidedBy;
    private Instant decidedAt;
    private String decisionReason;
    private Instant invalidatedAt;
    private String invalidationReason;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public ApprovalRequest(
            UUID approvalRequestId,
            UUID taskId,
            UUID companyId,
            long targetTaskVersion,
            long targetContentRevision,
            Long approvedTaskVersion,
            String targetFingerprint,
            ApprovalStatus status,
            String aiSnapshotJson,
            String hrSnapshotJson,
            String changedFieldsJson,
            String sourceVersionsJson,
            UUID requestedBy,
            Instant requestedAt,
            UUID decidedBy,
            Instant decidedAt,
            String decisionReason,
            Instant invalidatedAt,
            String invalidationReason,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.approvalRequestId = Objects.requireNonNull(approvalRequestId);
        this.taskId = Objects.requireNonNull(taskId);
        this.companyId = Objects.requireNonNull(companyId);
        this.targetTaskVersion = targetTaskVersion;
        if (targetContentRevision < 0) {
            throw new IllegalArgumentException("targetContentRevision must not be negative");
        }
        this.targetContentRevision = targetContentRevision;
        this.approvedTaskVersion = approvedTaskVersion;
        this.targetFingerprint = requireText(targetFingerprint);
        this.status = Objects.requireNonNull(status);
        this.aiSnapshotJson = normalizeNullable(aiSnapshotJson);
        this.hrSnapshotJson = requireText(hrSnapshotJson);
        this.changedFieldsJson = requireText(changedFieldsJson);
        this.sourceVersionsJson = requireText(sourceVersionsJson);
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionReason = normalizeNullable(decisionReason);
        this.invalidatedAt = invalidatedAt;
        this.invalidationReason = normalizeNullable(invalidationReason);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static ApprovalRequest create(
            UUID approvalRequestId,
            UUID taskId,
            UUID companyId,
            long targetTaskVersion,
            long targetContentRevision,
            String targetFingerprint,
            String aiSnapshotJson,
            String hrSnapshotJson,
            String changedFieldsJson,
            String sourceVersionsJson,
            UUID requestedBy,
            Instant now
    ) {
        return new ApprovalRequest(
                approvalRequestId,
                taskId,
                companyId,
                targetTaskVersion,
                targetContentRevision,
                null,
                targetFingerprint,
                ApprovalStatus.PENDING,
                aiSnapshotJson,
                hrSnapshotJson,
                changedFieldsJson,
                sourceVersionsJson,
                requestedBy,
                now,
                null,
                null,
                null,
                null,
                null,
                now,
                now,
                0
        );
    }

    public void approve(
            long currentTaskVersion,
            long currentContentRevision,
            String currentFingerprint,
            long approvedTaskVersion,
            UUID actorId,
            String reason,
            Instant now
    ) {
        requirePending();
        if (targetTaskVersion != currentTaskVersion
                || targetContentRevision != currentContentRevision
                || !targetFingerprint.equals(currentFingerprint)) {
            throw new ApiException(ApprovalErrorCode.APPROVAL_VERSION_MISMATCH);
        }
        status = ApprovalStatus.APPROVED;
        this.approvedTaskVersion = approvedTaskVersion;
        decidedBy = Objects.requireNonNull(actorId);
        decidedAt = Objects.requireNonNull(now);
        decisionReason = normalizeNullable(reason);
        updatedAt = now;
    }

    public void reject(
            long currentTaskVersion,
            UUID actorId,
            String reason,
            Instant now
    ) {
        requirePending();
        if (targetTaskVersion != currentTaskVersion) {
            throw new ApiException(ApprovalErrorCode.APPROVAL_VERSION_MISMATCH);
        }
        status = ApprovalStatus.REJECTED;
        decidedBy = Objects.requireNonNull(actorId);
        decidedAt = Objects.requireNonNull(now);
        decisionReason = requireText(reason);
        updatedAt = now;
    }

    public void invalidate(String reason, Instant now) {
        if (status != ApprovalStatus.PENDING && status != ApprovalStatus.APPROVED) {
            return;
        }
        status = ApprovalStatus.INVALIDATED;
        invalidatedAt = Objects.requireNonNull(now);
        invalidationReason = requireText(reason);
        updatedAt = now;
    }

    public boolean isValidFor(long contentRevision, String fingerprint) {
        return status == ApprovalStatus.APPROVED
                && targetContentRevision == contentRevision
                && targetFingerprint.equals(fingerprint);
    }

    private void requirePending() {
        if (status != ApprovalStatus.PENDING) {
            throw new ApiException(ApprovalErrorCode.APPROVAL_NOT_PENDING);
        }
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID approvalRequestId() {
        return approvalRequestId;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID companyId() {
        return companyId;
    }

    public long targetTaskVersion() {
        return targetTaskVersion;
    }

    public long targetContentRevision() {
        return targetContentRevision;
    }

    public Long approvedTaskVersion() {
        return approvedTaskVersion;
    }

    public String targetFingerprint() {
        return targetFingerprint;
    }

    public ApprovalStatus status() {
        return status;
    }

    public String aiSnapshotJson() {
        return aiSnapshotJson;
    }

    public String hrSnapshotJson() {
        return hrSnapshotJson;
    }

    public String changedFieldsJson() {
        return changedFieldsJson;
    }

    public String sourceVersionsJson() {
        return sourceVersionsJson;
    }

    public UUID requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public UUID decidedBy() {
        return decidedBy;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public String decisionReason() {
        return decisionReason;
    }

    public Instant invalidatedAt() {
        return invalidatedAt;
    }

    public String invalidationReason() {
        return invalidationReason;
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
