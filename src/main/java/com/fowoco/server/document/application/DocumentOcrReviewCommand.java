package com.fowoco.server.document.application;

import com.fowoco.server.document.domain.DocumentOcrReviewDecision;
import java.util.Objects;

public record DocumentOcrReviewCommand(
        long expectedVersion,
        DocumentOcrReviewDecision decision,
        String reason
) {
    public DocumentOcrReviewCommand {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        Objects.requireNonNull(decision, "decision must not be null");
    }
}
