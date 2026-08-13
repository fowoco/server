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
        List<String> requiredFieldKeys,
        String workflowId,
        String evidence,
        AiConfidenceSource confidenceSource,
        BigDecimal bertRoutingScore
) {

    public AiContextRequirement(
            String detectedIntent,
            BigDecimal confidence,
            String targetDisplayName,
            Map<String, String> extractedSlots,
            List<String> requiredFieldKeys
    ) {
        this(
                detectedIntent,
                confidence,
                targetDisplayName,
                extractedSlots,
                requiredFieldKeys,
                null,
                null,
                confidence == null ? AiConfidenceSource.UNAVAILABLE : AiConfidenceSource.MODEL,
                null
        );
    }

    public AiContextRequirement {
        Objects.requireNonNull(detectedIntent, "detectedIntent must not be null");
        Objects.requireNonNull(targetDisplayName, "targetDisplayName must not be null");
        Objects.requireNonNull(extractedSlots, "extractedSlots must not be null");
        Objects.requireNonNull(requiredFieldKeys, "requiredFieldKeys must not be null");
        if (confidenceSource == null) {
            confidenceSource = confidence == null
                    ? AiConfidenceSource.UNAVAILABLE
                    : AiConfidenceSource.MODEL;
        }
        extractedSlots = Map.copyOf(extractedSlots);
        requiredFieldKeys = List.copyOf(requiredFieldKeys);
    }
}
