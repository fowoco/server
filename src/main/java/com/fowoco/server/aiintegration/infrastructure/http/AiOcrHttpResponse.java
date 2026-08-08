package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentSide;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.ocr.AiOcrStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record AiOcrHttpResponse(
        UUID requestId,
        UUID workerDocumentId,
        AiOcrStatus ocrStatus,
        Long matchedTemplateId,
        AiOcrDocumentSide documentSide,
        Map<String, String> fields,
        Map<String, BigDecimal> fieldConfidences,
        List<String> reviewReasons
) {
    AiOcrResponse toDomain() {
        return new AiOcrResponse(
                requestId,
                workerDocumentId,
                ocrStatus,
                matchedTemplateId,
                documentSide,
                fields,
                fieldConfidences,
                reviewReasons
        );
    }
}
