package com.fowoco.server.aiintegration.application.error;

import java.util.Objects;

/**
 * Contract failure that never includes the rejected raw value in its message.
 */
public final class AiRuntimeContractException extends RuntimeException {

    private final AiRuntimeFailureCode failureCode;

    public AiRuntimeContractException(AiRuntimeFailureCode failureCode, String safeMessage) {
        super(safeMessage);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode must not be null");
    }

    public AiRuntimeFailureCode failureCode() {
        return failureCode;
    }
}
