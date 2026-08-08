package com.fowoco.server.workerlink.application;

import com.fowoco.server.workerlink.domain.WorkerLinkDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkerLinkIssueResult(
        UUID workerLinkId,
        String rawToken,
        Instant expiresAt,
        WorkerLinkDeliveryStatus deliveryStatus,
        Instant sentAt,
        boolean alreadyIssued
) {
}
