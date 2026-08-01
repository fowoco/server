package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.workerlink.application.WorkerLinkIssueResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "WorkerLinkIssueResponse", description = "근로자 보안 링크 발급 결과")
public final class WorkerLinkIssueResponse {

    @JsonProperty("worker_url")
    @Schema(name = "worker_url", description = "근로자에게 전달할 링크 URL")
    private final String workerUrl;

    @JsonProperty("expires_at")
    @Schema(name = "expires_at")
    private final Instant expiresAt;

    private WorkerLinkIssueResponse(String workerUrl, Instant expiresAt) {
        this.workerUrl = workerUrl;
        this.expiresAt = expiresAt;
    }

    public static WorkerLinkIssueResponse from(WorkerLinkIssueResult result) {
        // TODO: workerUrl 조립 방식 확정 필요.
        // 확인 후 실제 프론트 base URL + 경로로 조립해야 함.
        // 지금은 원문 토큰만 그대로 노출한 상태(미완성).
        return new WorkerLinkIssueResponse(result.rawToken(), result.expiresAt());
    }

    public String getWorkerUrl() {
        return workerUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
