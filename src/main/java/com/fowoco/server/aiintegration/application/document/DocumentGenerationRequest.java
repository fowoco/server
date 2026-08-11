package com.fowoco.server.aiintegration.application.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DocumentGenerationRequest(
        String templateId,
        String format,
        Map<String, Object> values
) {
    public DocumentGenerationRequest {
        templateId = requireText(templateId, "templateId");
        format = requireText(format, "format");
        values = values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
