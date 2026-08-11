package com.fowoco.server.aiintegration.application.model;

/**
 * Describes what produced a confidence value.
 *
 * <p>A.X does not expose a calibrated classification probability, so its final decision uses
 * {@link #UNAVAILABLE}. A BERT routing score is carried separately and must not be presented as
 * A.X confidence.</p>
 */
public enum AiConfidenceSource {
    MODEL,
    BERT,
    UNAVAILABLE
}
