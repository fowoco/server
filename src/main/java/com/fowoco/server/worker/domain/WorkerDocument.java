package com.fowoco.server.worker.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class WorkerDocument {

    private static final int MAX_DESTINATION_LENGTH = 120;
    private static final int MAX_NOTE_LENGTH = 500;

    private final UUID workerDocumentId;
    private final UUID workerId;
    private final UUID companyId;
    private final DocumentType documentType;
    private final SubmissionStatus submissionStatus;
    private final LocalDate expiryDate;
    private final String destination;
    private final String note;
    private final UUID fileId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public WorkerDocument(
            UUID workerDocumentId,
            UUID workerId,
            UUID companyId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            LocalDate expiryDate,
            String destination,
            String note,
            UUID fileId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.workerDocumentId = Objects.requireNonNull(workerDocumentId, "workerDocumentId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.documentType = Objects.requireNonNull(documentType, "documentType must not be null");
        this.submissionStatus = Objects.requireNonNull(submissionStatus, "submissionStatus must not be null");
        this.expiryDate = expiryDate;
        this.destination = requireMaxLength(destination, MAX_DESTINATION_LENGTH, "destination");
        this.note = requireMaxLength(note, MAX_NOTE_LENGTH, "note");
        this.fileId = fileId;
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

    public static WorkerDocument create(
            UUID workerDocumentId,
            UUID workerId,
            UUID companyId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            LocalDate expiryDate,
            String destination,
            String note,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        return new WorkerDocument(
                workerDocumentId,
                workerId,
                companyId,
                documentType,
                submissionStatus,
                expiryDate,
                destination,
                note,
                null,
                now,
                now,
                0L
        );
    }

    public UUID workerDocumentId() {
        return workerDocumentId;
    }

    public UUID workerId() {
        return workerId;
    }

    public UUID companyId() {
        return companyId;
    }

    public DocumentType documentType() {
        return documentType;
    }

    public SubmissionStatus submissionStatus() {
        return submissionStatus;
    }

    public LocalDate expiryDate() {
        return expiryDate;
    }

    public String destination() {
        return destination;
    }

    public String note() {
        return note;
    }

    public UUID fileId() {
        return fileId;
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

    private static String requireMaxLength(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
