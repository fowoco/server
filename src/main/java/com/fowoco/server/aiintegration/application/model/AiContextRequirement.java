package com.fowoco.server.aiintegration.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured data request returned by the Runtime instead of querying the Server database directly.
 */
public record AiContextRequirement(
        String detectedIntent,
        BigDecimal confidence,
        String targetDisplayName,
        Map<String, String> extractedSlots,
        List<String> requiredFieldKeys
) {

    public AiContextRequirement {
        Objects.requireNonNull(detectedIntent, "detectedIntent must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(targetDisplayName, "targetDisplayName must not be null");
        Objects.requireNonNull(extractedSlots, "extractedSlots must not be null");
        Objects.requireNonNull(requiredFieldKeys, "requiredFieldKeys must not be null");
        extractedSlots = Map.copyOf(extractedSlots);
        requiredFieldKeys = List.copyOf(requiredFieldKeys);
    }
}
