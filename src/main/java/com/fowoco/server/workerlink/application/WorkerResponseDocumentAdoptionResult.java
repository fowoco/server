package com.fowoco.server.workerlink.application;

import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.worker.domain.DocumentType;
import java.util.List;
import java.util.UUID;

public record WorkerResponseDocumentAdoptionResult(
        UUID responseId,
        List<AdoptedDocument> adoptedDocuments,
        TaskStatus taskStatus,
        long taskVersion
) {
    public WorkerResponseDocumentAdoptionResult {
        adoptedDocuments = List.copyOf(adoptedDocuments);
    }

    public record AdoptedDocument(
            UUID workerDocumentId,
            UUID fileId,
            DocumentType documentType
    ) {
    }
}
