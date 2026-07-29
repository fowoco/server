package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.time.LocalDate;
import java.util.UUID;

public final class WorkerDocumentPatchCommand {

    private final UUID workerDocumentId;
    private final UUID workerId;
    private final DocumentType documentType;
    private final SubmissionStatus submissionStatus;
    private final LocalDate expiryDate;
    private final String destination;
    private final String note;
    private final UUID fileId;
    private final long expectedVersion;

    public WorkerDocumentPatchCommand(
            UUID workerDocumentId,
            UUID workerId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            LocalDate expiryDate,
            String destination,
            String note,
            UUID fileId,
            long expectedVersion
    ) {
        this.workerDocumentId = workerDocumentId;
        this.workerId = workerId;
        this.documentType = documentType;
        this.submissionStatus = submissionStatus;
        this.expiryDate = expiryDate;
        this.destination = destination;
        this.note = note;
        this.fileId = fileId;
        this.expectedVersion = expectedVersion;
    }

    public UUID workerDocumentId() {
        return workerDocumentId;
    }

    public UUID workerId() {
        return workerId;
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

    public long expectedVersion() {
        return expectedVersion;
    }
}
