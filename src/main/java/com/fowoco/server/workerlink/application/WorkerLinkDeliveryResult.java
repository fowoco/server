package com.fowoco.server.workerlink.application;

import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerLinkDeliveryStatus;
import com.fowoco.server.workerlink.domain.WorkerLinkStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkerLinkDeliveryResult(
        UUID workerLinkId,
        WorkerLinkStatus linkStatus,
        WorkerLinkDeliveryStatus deliveryStatus,
        Instant sentAt,
        Instant expiresAt
) {
    public static WorkerLinkDeliveryResult from(WorkerLink link) {
        return new WorkerLinkDeliveryResult(
                link.workerLinkId(),
                link.status(),
                link.deliveryStatus(),
                link.sentAt(),
                link.expiresAt()
        );
    }
}
