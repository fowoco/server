package com.fowoco.server.worker.domain;

public enum SubmissionStatus {
    DRAFT,
    MISSING,
    SUBMITTED,
    VERIFIED;

    public boolean isVerified() {
        return this == VERIFIED;
    }
}
