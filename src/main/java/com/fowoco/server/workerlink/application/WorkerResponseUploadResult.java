package com.fowoco.server.workerlink.application;

import com.fowoco.server.worker.domain.DocumentType;
import java.util.UUID;

public record WorkerResponseUploadResult(
        UUID fileId,
        String fileName,
        String mimeType,
        long size,
        DocumentType documentType,
        boolean adopted
) {
}
