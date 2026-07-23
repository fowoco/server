package com.fowoco.server.worker.domain;

public enum WorkerStatus {
    ACTIVE,
    ON_LEAVE,
    RESIGNED,
    TERMINATED;

    public boolean isCurrentlyEmployed() {
        return this == ACTIVE || this == ON_LEAVE;
    }
}
