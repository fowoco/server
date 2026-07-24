package com.fowoco.server.reliability.application;

public class NonRetryableEventHandlingException extends RuntimeException {

    private final String errorCode;

    public NonRetryableEventHandlingException(String errorCode) {
        super("Non-retryable event handler failure.");
        this.errorCode = requireErrorCode(errorCode);
    }

    public String errorCode() {
        return errorCode;
    }

    private static String requireErrorCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("^[A-Z][A-Z0-9_]{1,79}$")) {
            throw new IllegalArgumentException("Invalid safe event error code.");
        }
        return errorCode;
    }
}
