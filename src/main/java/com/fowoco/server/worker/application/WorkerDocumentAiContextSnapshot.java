package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Non-sensitive WorkerDocument facts exposed to the AiRun context boundary.
 */
public record WorkerDocumentAiContextSnapshot(
        DocumentType documentType,
        SubmissionStatus submissionStatus,
        LocalDate expiryDate
) {

    public WorkerDocumentAiContextSnapshot {
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(submissionStatus, "submissionStatus must not be null");
    }
}
