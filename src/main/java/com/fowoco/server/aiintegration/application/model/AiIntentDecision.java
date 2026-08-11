package com.fowoco.server.aiintegration.application.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The single representative Intent and Workflow decision fixed during PLAN for the MVP.
 */
public record AiIntentDecision(
        String detectedIntent,
        String workflowId,
        String evidence,
        BigDecimal confidence,
        AiConfidenceSource confidenceSource,
        BigDecimal bertRoutingScore
) {

    public AiIntentDecision {
        Objects.requireNonNull(detectedIntent, "detectedIntent must not be null");
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(confidenceSource, "confidenceSource must not be null");
    }

    public static AiIntentDecision from(AiContextRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement must not be null");
        return new AiIntentDecision(
                requirement.detectedIntent(),
                requirement.workflowId(),
                requirement.evidence(),
                requirement.confidence(),
                requirement.confidenceSource(),
                requirement.bertRoutingScore()
        );
    }
}
