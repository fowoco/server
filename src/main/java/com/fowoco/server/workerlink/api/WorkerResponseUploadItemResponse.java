package com.fowoco.server.workerlink.api;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.workerlink.application.WorkerResponseUploadResult;
import java.util.UUID;

public record WorkerResponseUploadItemResponse(
        UUID fileId,
        String fileName,
        String mimeType,
        long size,
        DocumentType documentType,
        boolean adopted
) {
    static WorkerResponseUploadItemResponse from(WorkerResponseUploadResult result) {
        return new WorkerResponseUploadItemResponse(
                result.fileId(),
                result.fileName(),
                result.mimeType(),
                result.size(),
                result.documentType(),
                result.adopted()
        );
    }
}
