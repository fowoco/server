package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.workerlink.application.WorkerResponseSubmitResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "WorkerResponseSubmitResponse", description = "근로자 응답 제출 결과")
public final class WorkerResponseSubmitResponse {

    @JsonProperty("response_id")
    @Schema(name = "response_id")
    private final UUID responseId;

    @JsonProperty("received_at")
    @Schema(name = "received_at")
    private final Instant receivedAt;

    private WorkerResponseSubmitResponse(UUID responseId, Instant receivedAt) {
        this.responseId = responseId;
        this.receivedAt = receivedAt;
    }

    public static WorkerResponseSubmitResponse from(WorkerResponseSubmitResult result) {
        return new WorkerResponseSubmitResponse(result.responseId(), result.receivedAt());
    }

    public UUID getResponseId() {
        return responseId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
