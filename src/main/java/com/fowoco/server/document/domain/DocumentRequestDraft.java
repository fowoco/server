package com.fowoco.server.document.domain;

import com.fowoco.server.worker.domain.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class DocumentRequestDraft {

    private static final int MAX_LANGUAGE_LENGTH = 20;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final UUID draftId;
    private final UUID taskId;
    private final UUID companyId;
    private final String language;
    private final List<DocumentType> documentTypes;
    private final String message;
    private final DocumentRequestReviewStatus reviewStatus;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public DocumentRequestDraft(
            UUID draftId,
            UUID taskId,
            UUID companyId,
            String language,
            List<DocumentType> documentTypes,
            String message,
            DocumentRequestReviewStatus reviewStatus,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.draftId = Objects.requireNonNull(draftId, "draftId must not be null");
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.language = requireBounded(language, MAX_LANGUAGE_LENGTH, "language");
        if (documentTypes == null || documentTypes.isEmpty()) {
            throw new IllegalArgumentException("documentTypes must not be empty");
        }
        this.documentTypes = List.copyOf(documentTypes);
        this.message = message == null ? null : requireBounded(message, MAX_MESSAGE_LENGTH, "message");
        this.reviewStatus = Objects.requireNonNull(reviewStatus, "reviewStatus must not be null");
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

    public static DocumentRequestDraft create(
            UUID draftId,
            UUID taskId,
            UUID companyId,
            String language,
            List<DocumentType> documentTypes,
            String message,
            Instant now
    ) {
        return new DocumentRequestDraft(
                draftId,
                taskId,
                companyId,
                language,
                documentTypes,
                message,
                DocumentRequestReviewStatus.DRAFT,
                now,
                now,
                0L
        );
    }

    public DocumentRequestDraft withUpdatedContent(
            String language,
            List<DocumentType> documentTypes,
            String message,
            Instant now
    ) {
        return new DocumentRequestDraft(
                draftId,
                taskId,
                companyId,
                language,
                documentTypes,
                message,
                reviewStatus,
                createdAt,
                now,
                version
        );
    }

    private static String requireBounded(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    public UUID draftId() {
        return draftId;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String language() {
        return language;
    }

    public List<DocumentType> documentTypes() {
        return documentTypes;
    }

    public String message() {
        return message;
    }

    public DocumentRequestReviewStatus reviewStatus() {
        return reviewStatus;
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
