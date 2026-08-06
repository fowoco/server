package com.fowoco.server.workerlink.application;

import java.util.UUID;

public final class WorkerLinkIssueCommand {

    private final UUID taskId;
    private final Long expiresInHours;
    private final boolean rotateExisting;
    private final String idempotencyKey;

    public WorkerLinkIssueCommand(
            UUID taskId,
            Long expiresInHours,
            boolean rotateExisting,
            String idempotencyKey
    ) {
        this.taskId = taskId;
        this.expiresInHours = expiresInHours;
        this.rotateExisting = rotateExisting;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID taskId() {
        return taskId;
    }

    public Long expiresInHours() {
        return expiresInHours;
    }

    public boolean rotateExisting() {
        return rotateExisting;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
