package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.workerlink.application.WorkerLinkDeliveryResult;
import com.fowoco.server.workerlink.domain.WorkerLinkDeliveryStatus;
import com.fowoco.server.workerlink.domain.WorkerLinkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "WorkerLinkDeliveryResponse", description = "HR용 근로자 링크 전달 상태")
public record WorkerLinkDeliveryResponse(
        @JsonProperty("worker_link_id") UUID workerLinkId,
        @JsonProperty("link_status") WorkerLinkStatus linkStatus,
        @JsonProperty("delivery_status") WorkerLinkDeliveryStatus deliveryStatus,
        @JsonProperty("sent_at") Instant sentAt,
        @JsonProperty("expires_at") Instant expiresAt
) {
    public static WorkerLinkDeliveryResponse from(WorkerLinkDeliveryResult result) {
        return new WorkerLinkDeliveryResponse(
                result.workerLinkId(),
                result.linkStatus(),
                result.deliveryStatus(),
                result.sentAt(),
                result.expiresAt()
        );
    }
}
