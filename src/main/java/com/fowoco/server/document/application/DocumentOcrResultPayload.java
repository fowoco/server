package com.fowoco.server.document.application;

import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentSide;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DocumentOcrResultPayload(
        Long matchedTemplateId,
        AiOcrDocumentSide documentSide,
        Map<String, String> fields,
        Map<String, BigDecimal> fieldConfidences,
        List<String> reviewReasons
) {
    public DocumentOcrResultPayload {
        fields = Map.copyOf(fields);
        fieldConfidences = Map.copyOf(fieldConfidences);
        reviewReasons = List.copyOf(reviewReasons);
    }
}
