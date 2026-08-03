package com.fowoco.server.airun.application.error;

import java.util.Objects;

/**
 * Resolution failure whose message never includes a Worker name or field value.
 */
public final class AiContextResolutionException extends RuntimeException {

    private final AiContextResolutionFailureCode failureCode;

    public AiContextResolutionException(
            AiContextResolutionFailureCode failureCode,
            String safeMessage
    ) {
        super(safeMessage);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
    }

    public AiContextResolutionFailureCode failureCode() {
        return failureCode;
    }
}
