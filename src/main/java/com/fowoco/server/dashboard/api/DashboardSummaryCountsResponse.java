package com.fowoco.server.dashboard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DashboardSummaryCountsResponse", description = "오늘 대시보드 상태별 개수")
public final class DashboardSummaryCountsResponse {

    @JsonProperty("pending_approval")
    @Schema(name = "pending_approval", description = "승인 대기(READY_FOR_REVIEW) 개수")
    private final long pendingApproval;

    @JsonProperty("due_today")
    @Schema(name = "due_today", description = "오늘 마감인 업무 개수")
    private final long dueToday;

    @JsonProperty("needs_info")
    @Schema(name = "needs_info", description = "정보 보완(NEEDS_INFO) 개수")
    private final long needsInfo;

    @JsonProperty("worker_response")
    @Schema(name = "worker_response", description = "근로자 응답 대기(WAITING_WORKER) 개수")
    private final long workerResponse;

    public DashboardSummaryCountsResponse(long pendingApproval, long dueToday, long needsInfo, long workerResponse) {
        this.pendingApproval = pendingApproval;
        this.dueToday = dueToday;
        this.needsInfo = needsInfo;
        this.workerResponse = workerResponse;
    }

    public long getPendingApproval() {
        return pendingApproval;
    }

    public long getDueToday() {
        return dueToday;
    }

    public long getNeedsInfo() {
        return needsInfo;
    }

    public long getWorkerResponse() {
        return workerResponse;
    }
}
