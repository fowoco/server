package com.fowoco.server.aiintegration.application.model;

import java.util.Objects;

/**
 * Machine-readable validation result. Free-form Provider error messages are intentionally omitted.
 */
public record AiValidationError(
        String code,
        String field
) {

    public AiValidationError {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(field, "field must not be null");
    }
}
