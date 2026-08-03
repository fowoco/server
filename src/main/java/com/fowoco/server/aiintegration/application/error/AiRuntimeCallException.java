package com.fowoco.server.aiintegration.application.error;

import java.util.Objects;

/**
 * Stable transport failure that does not expose a credential, URI, or Runtime response body.
 */
public final class AiRuntimeCallException extends RuntimeException {

    private final AiRuntimeFailureCode failureCode;

    public AiRuntimeCallException(AiRuntimeFailureCode failureCode, String safeMessage) {
        super(safeMessage);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
    }

    public AiRuntimeCallException(
            AiRuntimeFailureCode failureCode,
            String safeMessage,
            Throwable cause
    ) {
        super(safeMessage, cause);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
    }

    public AiRuntimeFailureCode failureCode() {
        return failureCode;
    }
}
