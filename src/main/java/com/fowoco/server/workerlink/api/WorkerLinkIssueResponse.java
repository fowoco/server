package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.workerlink.application.WorkerLinkIssueResult;
import com.fowoco.server.workerlink.domain.WorkerLinkDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "WorkerLinkIssueResponse", description = "근로자 보안 링크 발급 결과")
public final class WorkerLinkIssueResponse {

    @JsonProperty("worker_link_id")
    @Schema(name = "worker_link_id", description = "발급된 근로자 링크 ID")
    private final UUID workerLinkId;

    @JsonProperty("worker_url")
    @Schema(name = "worker_url", description = "근로자에게 전달할 링크 URL. 같은 idempotency key로 재요청한 경우 null")
    private final String workerUrl;

    @JsonProperty("worker_link_token")
    @Schema(
            name = "worker_link_token",
            description = "SMS 발송 API와 근로자 공개 API에 사용할 원본 token. 발급 직후에만 반환"
    )
    private final String workerLinkToken;

    @JsonProperty("expires_at")
    @Schema(name = "expires_at")
    private final Instant expiresAt;

    @JsonProperty("delivery_status")
    @Schema(name = "delivery_status", description = "HR의 링크 전달 완료 기록 상태")
    private final WorkerLinkDeliveryStatus deliveryStatus;

    @JsonProperty("sent_at")
    @Schema(name = "sent_at", description = "HR이 전달 완료를 기록한 서버 시각. 미전송이면 null")
    private final Instant sentAt;

    @JsonProperty("already_issued")
    @Schema(name = "already_issued", description = "같은 idempotency key로 이미 발급된 적이 있어 재사용된 응답인지 여부")
    private final boolean alreadyIssued;

    private WorkerLinkIssueResponse(
            UUID workerLinkId,
            String workerUrl,
            String workerLinkToken,
            Instant expiresAt,
            WorkerLinkDeliveryStatus deliveryStatus,
            Instant sentAt,
            boolean alreadyIssued
    ) {
        this.workerLinkId = workerLinkId;
        this.workerUrl = workerUrl;
        this.workerLinkToken = workerLinkToken;
        this.expiresAt = expiresAt;
        this.deliveryStatus = deliveryStatus;
        this.sentAt = sentAt;
        this.alreadyIssued = alreadyIssued;
    }

    public static WorkerLinkIssueResponse from(WorkerLinkIssueResult result, String workerUrl) {
        return new WorkerLinkIssueResponse(
                result.workerLinkId(),
                workerUrl,
                result.rawToken(),
                result.expiresAt(),
                result.deliveryStatus(),
                result.sentAt(),
                result.alreadyIssued()
        );
    }

    public UUID getWorkerLinkId() {
        return workerLinkId;
    }

    public String getWorkerUrl() {
        return workerUrl;
    }

    public String getWorkerLinkToken() {
        return workerLinkToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public WorkerLinkDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public boolean isAlreadyIssued() {
        return alreadyIssued;
    }
}
