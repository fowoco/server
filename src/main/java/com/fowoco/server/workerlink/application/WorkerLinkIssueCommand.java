package com.fowoco.server.workerlink.application;

import java.util.UUID;

public final class WorkerLinkIssueCommand {

    private final UUID taskId;
    private final UUID companyId;
    private final Long expiresInHours;
    private final boolean rotateExisting;
    private final UUID issuedBy;

    public WorkerLinkIssueCommand(
            UUID taskId,
            UUID companyId,
            Long expiresInHours,
            boolean rotateExisting,
            UUID issuedBy
    ) {
        this.taskId = taskId;
        this.companyId = companyId;
        this.expiresInHours = expiresInHours;
        this.rotateExisting = rotateExisting;
        this.issuedBy = issuedBy;
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
}
