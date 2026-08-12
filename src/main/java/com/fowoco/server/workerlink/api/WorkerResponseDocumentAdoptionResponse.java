package com.fowoco.server.workerlink.api;

import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.workerlink.application.WorkerResponseDocumentAdoptionResult;
import java.util.List;
import java.util.UUID;

public record WorkerResponseDocumentAdoptionResponse(
        UUID responseId,
        List<AdoptedDocumentResponse> adoptedDocuments,
        TaskStatus taskStatus,
        long taskVersion
) {
    static WorkerResponseDocumentAdoptionResponse from(WorkerResponseDocumentAdoptionResult result) {
        return new WorkerResponseDocumentAdoptionResponse(
                result.responseId(),
                result.adoptedDocuments().stream().map(AdoptedDocumentResponse::from).toList(),
                result.taskStatus(),
                result.taskVersion()
        );
    }

    public record AdoptedDocumentResponse(
            UUID workerDocumentId,
            UUID fileId,
            DocumentType documentType
    ) {
        static AdoptedDocumentResponse from(
                WorkerResponseDocumentAdoptionResult.AdoptedDocument document
        ) {
            return new AdoptedDocumentResponse(
                    document.workerDocumentId(),
                    document.fileId(),
                    document.documentType()
            );
        }
    }
}
