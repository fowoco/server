package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.time.LocalDate;
import java.util.UUID;

public final class WorkerDocumentSearchQuery {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private final UUID workerId;
    private final UUID taskId;
    private final DocumentType documentType;
    private final SubmissionStatus status;
    private final LocalDate expiryBefore;
    private final int page;
    private final int size;

    public WorkerDocumentSearchQuery(
            UUID workerId,
            UUID taskId,
            DocumentType documentType,
            SubmissionStatus status,
            LocalDate expiryBefore,
            Integer page,
            Integer size
    ) {
        this.workerId = workerId;
        this.taskId = taskId;
        this.documentType = documentType;
        this.status = status;
        this.expiryBefore = expiryBefore;
        this.page = page == null ? 0 : page;
        this.size = size == null ? 20 : size;
        if (this.page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (this.size < MIN_SIZE || this.size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between " + MIN_SIZE + " and " + MAX_SIZE);
        }
    }

    public UUID workerId() {
        return workerId;
    }

    public UUID taskId() {
        return taskId;
    }

    public DocumentType documentType() {
        return documentType;
    }

    public SubmissionStatus status() {
        return status;
    }

    public LocalDate expiryBefore() {
        return expiryBefore;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }
}
