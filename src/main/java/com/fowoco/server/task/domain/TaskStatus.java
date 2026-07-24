package com.fowoco.server.task.domain;

public enum TaskStatus {
    DRAFT,
    NEEDS_INFO,
    READY_FOR_REVIEW,
    APPROVED,
    WAITING_WORKER,
    WAITING_EXTERNAL,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
