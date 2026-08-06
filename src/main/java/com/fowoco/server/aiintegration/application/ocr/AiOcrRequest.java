package com.fowoco.server.aiintegration.application.ocr;

import java.util.Objects;
import java.util.UUID;

public record AiOcrRequest(
        UUID requestId,
        UUID workerDocumentId,
        AiOcrDocumentType documentType,
        String countryCode,
        AiOcrFile file
) {

    public AiOcrRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(workerDocumentId, "workerDocumentId must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(file, "file must not be null");
    }
}
