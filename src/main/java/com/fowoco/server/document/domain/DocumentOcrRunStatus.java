package com.fowoco.server.document.domain;

public enum DocumentOcrRunStatus {
    QUEUED,
    RUNNING,
    READY_FOR_REVIEW,
    REVIEW_REQUIRED,
    APPROVED,
    REJECTED,
    FAILED;

    public boolean hasResult() {
        return this == READY_FOR_REVIEW
                || this == REVIEW_REQUIRED
                || this == APPROVED
                || this == REJECTED;
    }

    public boolean isReviewable() {
        return this == READY_FOR_REVIEW || this == REVIEW_REQUIRED;
    }
}
