package com.fowoco.server.aiintegration.application.model;

/**
 * A successful business outcome returned by the AI Runtime.
 *
 * <p>Low confidence and missing information are not transport failures.</p>
 */
public enum AiAnalysisOutcome {
    OUT_OF_SCOPE,
    CONTEXT_REQUIRED,
    NEEDS_INFO,
    REVIEW_REQUIRED
}
