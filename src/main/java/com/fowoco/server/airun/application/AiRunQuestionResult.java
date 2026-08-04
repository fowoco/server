package com.fowoco.server.airun.application;

import java.util.Objects;

public record AiRunQuestionResult(
        String slotKey,
        String label,
        String inputType,
        boolean required,
        String answer
) {
    public AiRunQuestionResult {
        Objects.requireNonNull(slotKey, "slotKey must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(inputType, "inputType must not be null");
    }
}
