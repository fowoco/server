package com.fowoco.server.document.domain;

import com.fowoco.server.aiintegration.application.ocr.AiOcrStatus;
import com.fowoco.server.worker.domain.DocumentType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DocumentOcrRun {

    private final UUID ocrRunId;
    private final UUID companyId;
    private final UUID workerDocumentId;
    private final UUID storedFileId;
    private final UUID requestedBy;
    private final UUID runtimeRequestId;
    private final String idempotencyKeyHash;
    private final String requestHash;
    private final DocumentType documentType;
    private final String countryCode;
    private final DocumentOcrRunStatus status;
    private final String resultCiphertext;
    private final String resultKeyVersion;
    private final String lastErrorCode;
    private final UUID reviewedBy;
    private final String reviewReason;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Instant reviewedAt;
    private final Instant updatedAt;
    private final long version;

    public DocumentOcrRun(
            UUID ocrRunId,
            UUID companyId,
            UUID workerDocumentId,
            UUID storedFileId,
            UUID requestedBy,
            UUID runtimeRequestId,
            String idempotencyKeyHash,
            String requestHash,
            DocumentType documentType,
            String countryCode,
            DocumentOcrRunStatus status,
            String resultCiphertext,
            String resultKeyVersion,
            String lastErrorCode,
            UUID reviewedBy,
            String reviewReason,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant reviewedAt,
            Instant updatedAt,
            long version
    ) {
        this.ocrRunId = Objects.requireNonNull(ocrRunId, "ocrRunId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.workerDocumentId = Objects.requireNonNull(workerDocumentId, "workerDocumentId must not be null");
        this.storedFileId = Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        this.runtimeRequestId = Objects.requireNonNull(runtimeRequestId, "runtimeRequestId must not be null");
        this.idempotencyKeyHash = requireText(idempotencyKeyHash, "idempotencyKeyHash");
        this.requestHash = requireText(requestHash, "requestHash");
        this.documentType = Objects.requireNonNull(documentType, "documentType must not be null");
        this.countryCode = normalize(countryCode);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.resultCiphertext = normalize(resultCiphertext);
        this.resultKeyVersion = normalize(resultKeyVersion);
        this.lastErrorCode = normalize(lastErrorCode);
        this.reviewedBy = reviewedBy;
        this.reviewReason = normalize(reviewReason);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.reviewedAt = reviewedAt;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public static DocumentOcrRun create(
            UUID ocrRunId,
            UUID companyId,
            UUID workerDocumentId,
            UUID storedFileId,
            UUID requestedBy,
            UUID runtimeRequestId,
            String idempotencyKeyHash,
            String requestHash,
            DocumentType documentType,
            String countryCode,
            Instant now
    ) {
        return new DocumentOcrRun(
                ocrRunId, companyId, workerDocumentId, storedFileId, requestedBy,
                runtimeRequestId, idempotencyKeyHash, requestHash, documentType,
                countryCode, DocumentOcrRunStatus.QUEUED, null, null, null,
                null, null, now, null, null, null, now, 0L
        );
    }

    public DocumentOcrRun start(Instant now) {
        requireStatus(DocumentOcrRunStatus.QUEUED);
        return copy(DocumentOcrRunStatus.RUNNING, null, null, null, null, null, now, null, null, now);
    }

    public DocumentOcrRun complete(
            AiOcrStatus outcome,
            String ciphertext,
            String keyVersion,
            Instant now
    ) {
        requireStatus(DocumentOcrRunStatus.RUNNING);
        DocumentOcrRunStatus next = outcome == AiOcrStatus.SUCCEEDED
                ? DocumentOcrRunStatus.READY_FOR_REVIEW
                : DocumentOcrRunStatus.REVIEW_REQUIRED;
        return copy(next, requireText(ciphertext, "ciphertext"), requireText(keyVersion, "keyVersion"),
                null, null, null, startedAt, now, null, now);
    }

    public DocumentOcrRun fail(String errorCode, Instant now) {
        if (status != DocumentOcrRunStatus.QUEUED && status != DocumentOcrRunStatus.RUNNING) {
            throw new IllegalStateException("only queued or running OCR can fail");
        }
        return copy(DocumentOcrRunStatus.FAILED, null, null, requireText(errorCode, "errorCode"),
                null, null, startedAt, now, null, now);
    }

    public DocumentOcrRun review(
            DocumentOcrReviewDecision decision,
            UUID reviewerId,
            String reason,
            Instant now
    ) {
        if (!status.isReviewable()) {
            throw new IllegalStateException("OCR result is not reviewable");
        }
        DocumentOcrRunStatus next = decision == DocumentOcrReviewDecision.APPROVE
                ? DocumentOcrRunStatus.APPROVED
                : DocumentOcrRunStatus.REJECTED;
        return copy(next, resultCiphertext, resultKeyVersion, null,
                Objects.requireNonNull(reviewerId, "reviewerId must not be null"), normalize(reason),
                startedAt, completedAt, now, now);
    }

    private DocumentOcrRun copy(
            DocumentOcrRunStatus nextStatus,
            String nextCiphertext,
            String nextKeyVersion,
            String nextErrorCode,
            UUID nextReviewedBy,
            String nextReviewReason,
            Instant nextStartedAt,
            Instant nextCompletedAt,
            Instant nextReviewedAt,
            Instant nextUpdatedAt
    ) {
        return new DocumentOcrRun(
                ocrRunId, companyId, workerDocumentId, storedFileId, requestedBy,
                runtimeRequestId, idempotencyKeyHash, requestHash, documentType, countryCode,
                nextStatus, nextCiphertext, nextKeyVersion, nextErrorCode, nextReviewedBy,
                nextReviewReason, createdAt, nextStartedAt, nextCompletedAt,
                nextReviewedAt, nextUpdatedAt, version
        );
    }

    private void requireStatus(DocumentOcrRunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("OCR status must be " + expected);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID ocrRunId() { return ocrRunId; }
    public UUID companyId() { return companyId; }
    public UUID workerDocumentId() { return workerDocumentId; }
    public UUID storedFileId() { return storedFileId; }
    public UUID requestedBy() { return requestedBy; }
    public UUID runtimeRequestId() { return runtimeRequestId; }
    public String idempotencyKeyHash() { return idempotencyKeyHash; }
    public String requestHash() { return requestHash; }
    public DocumentType documentType() { return documentType; }
    public String countryCode() { return countryCode; }
    public DocumentOcrRunStatus status() { return status; }
    public String resultCiphertext() { return resultCiphertext; }
    public String resultKeyVersion() { return resultKeyVersion; }
    public String lastErrorCode() { return lastErrorCode; }
    public UUID reviewedBy() { return reviewedBy; }
    public String reviewReason() { return reviewReason; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant reviewedAt() { return reviewedAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
