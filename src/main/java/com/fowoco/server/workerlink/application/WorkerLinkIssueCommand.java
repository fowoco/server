package com.fowoco.server.workerlink.application;

import java.util.UUID;

public final class WorkerLinkIssueCommand {

    private final UUID taskId;
    private final UUID companyId;
    private final Long expiresInHours;
    private final boolean rotateExisting;
    private final UUID issuedBy;
    private final String idempotencyKey;

    public WorkerLinkIssueCommand(
            UUID taskId,
            UUID companyId,
            Long expiresInHours,
            boolean rotateExisting,
            UUID issuedBy,
            String idempotencyKey
    ) {
        this.taskId = taskId;
        this.companyId = companyId;
        this.expiresInHours = expiresInHours;
        this.rotateExisting = rotateExisting;
        this.issuedBy = issuedBy;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID companyId() {
        return companyId;
    }

    public Long expiresInHours() {
        return expiresInHours;
    }

    public boolean rotateExisting() {
        return rotateExisting;
    }

    public UUID issuedBy() {
        return issuedBy;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }
}
