package com.fowoco.server.reliability.domain;

public enum EventPublicationStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    REVIEW_REQUIRED
}
