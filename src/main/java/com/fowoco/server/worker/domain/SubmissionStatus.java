package com.fowoco.server.worker.domain;

public enum SubmissionStatus {
    MISSING,
    SUBMITTED,
    VERIFIED;

    public boolean isVerified() {
        return this == VERIFIED;
    }
}
