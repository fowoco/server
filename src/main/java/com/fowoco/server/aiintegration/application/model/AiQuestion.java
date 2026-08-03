package com.fowoco.server.aiintegration.application.model;

import java.util.Objects;

/**
 * One missing field question that must be answered by an HR user.
 */
public record AiQuestion(
        String slotKey,
        String prompt
) {

    public AiQuestion {
        Objects.requireNonNull(slotKey, "slotKey must not be null");
        Objects.requireNonNull(prompt, "prompt must not be null");
    }
}
