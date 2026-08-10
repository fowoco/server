package com.fowoco.server.workerlink.application.port;

public final class WorkerLinkSmsProviderException extends RuntimeException {

    private final FailureType failureType;

    private WorkerLinkSmsProviderException(FailureType failureType, Throwable cause) {
        super("worker link SMS provider failed", cause);
        this.failureType = failureType;
    }

    public static WorkerLinkSmsProviderException disabled() {
        return new WorkerLinkSmsProviderException(FailureType.DISABLED, null);
    }

    public static WorkerLinkSmsProviderException deliveryFailed(Throwable cause) {
        return new WorkerLinkSmsProviderException(FailureType.DELIVERY_FAILED, cause);
    }

    public FailureType failureType() {
        return failureType;
    }

    public enum FailureType {
        DISABLED,
        DELIVERY_FAILED
    }
}
