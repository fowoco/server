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
        @JsonProperty("delivery_status")
        @Schema(description = "NOT_SENT, SENDING, REVIEW_REQUIRED, SENT 중 현재 전달 처리 상태")
        WorkerLinkDeliveryStatus deliveryStatus,
        @JsonProperty("sent_at")
        @Schema(description = "Provider 접수 또는 HR 수동 전달 기록 시각. 휴대전화 최종 수신 시각이 아님")
        Instant sentAt,
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
