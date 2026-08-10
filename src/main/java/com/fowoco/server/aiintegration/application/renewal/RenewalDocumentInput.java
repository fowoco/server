package com.fowoco.server.aiintegration.application.renewal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RenewalDocumentInput(
        String documentType,
        String filename,
        Map<String, Object> fields,
        Map<String, Object> hints
) {
    public RenewalDocumentInput {
        fields = immutableMap(fields);
        hints = immutableMap(hints);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
