package com.fowoco.server.aiintegration.application.ocr;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AiOcrResponse(
        UUID requestId,
        UUID workerDocumentId,
        AiOcrStatus status,
        Long matchedTemplateId,
        AiOcrDocumentSide documentSide,
        Map<String, String> fields,
        Map<String, BigDecimal> fieldConfidences,
        List<String> reviewReasons
) {

    public AiOcrResponse {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(workerDocumentId, "workerDocumentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        Objects.requireNonNull(fieldConfidences, "fieldConfidences must not be null");
        Objects.requireNonNull(reviewReasons, "reviewReasons must not be null");
        fields = Map.copyOf(fields);
        fieldConfidences = Map.copyOf(fieldConfidences);
        reviewReasons = List.copyOf(reviewReasons);
    }
}
