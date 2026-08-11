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

    public static WorkerLinkSmsProviderException rejected(Throwable cause) {
        return new WorkerLinkSmsProviderException(FailureType.REJECTED, cause);
    }

    public static WorkerLinkSmsProviderException unknown(Throwable cause) {
        return new WorkerLinkSmsProviderException(FailureType.UNKNOWN, cause);
    }

    public FailureType failureType() {
        return failureType;
    }

    public enum FailureType {
        DISABLED,
        REJECTED,
        UNKNOWN
    }
}
