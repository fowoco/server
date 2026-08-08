package com.fowoco.server.document.application;

import com.fowoco.server.document.domain.DocumentOcrReviewDecision;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DocumentOcrReviewCommand(
        long expectedVersion,
        DocumentOcrReviewDecision decision,
        String reason,
        Map<String, String> correctedFields
) {
    public DocumentOcrReviewCommand {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        Objects.requireNonNull(decision, "decision must not be null");
        correctedFields = correctedFields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(correctedFields));
    }
}
