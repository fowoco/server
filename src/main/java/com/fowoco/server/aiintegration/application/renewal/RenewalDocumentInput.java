package com.fowoco.server.aiintegration.application.renewal;

import java.util.Map;

public record RenewalDocumentInput(
        String documentType,
        String filename,
        Map<String, Object> fields,
        Map<String, Object> hints
) {
    public RenewalDocumentInput {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        hints = hints == null ? Map.of() : Map.copyOf(hints);
    }
}
