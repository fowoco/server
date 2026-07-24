package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.time.LocalDate;
import java.util.UUID;

public final class WorkerDocumentCreateCommand {

    private final UUID workerId;
    private final UUID companyId;
    private final DocumentType documentType;
    private final SubmissionStatus submissionStatus;
    private final LocalDate expiryDate;
    private final String destination;
    private final String note;

    public WorkerDocumentCreateCommand(
            UUID workerId,
            UUID companyId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            LocalDate expiryDate,
            String destination,
            String note
    ) {
        this.workerId = workerId;
        this.companyId = companyId;
        this.documentType = documentType;
        this.submissionStatus = submissionStatus;
        this.expiryDate = expiryDate;
        this.destination = destination;
        this.note = note;
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
}
