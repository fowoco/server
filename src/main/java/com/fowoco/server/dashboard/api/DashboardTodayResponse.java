package com.fowoco.server.dashboard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "DashboardTodayResponse", description = "오늘 대시보드 응답")
public final class DashboardTodayResponse {

    @JsonProperty("summary_counts")
    @Schema(name = "summary_counts")
    private final DashboardSummaryCountsResponse summaryCounts;

    @JsonProperty("priority_tasks")
    @Schema(name = "priority_tasks", description = "오늘의 우선 업무 (열린 업무, 마감일 순 최대 5건)")
    private final List<DashboardTaskSummaryResponse> priorityTasks;

    @JsonProperty("approval_count")
    @Schema(name = "approval_count", description = "승인 대기 개수 (summary_counts.pending_approval과 동일)")
    private final long approvalCount;

    @JsonProperty("worker_response_count")
    @Schema(name = "worker_response_count", description = "근로자 응답 대기 개수")
    private final long workerResponseCount;

    public DashboardTodayResponse(
            DashboardSummaryCountsResponse summaryCounts,
            List<DashboardTaskSummaryResponse> priorityTasks,
            long approvalCount,
            long workerResponseCount
    ) {
        this.summaryCounts = summaryCounts;
        this.priorityTasks = priorityTasks;
        this.approvalCount = approvalCount;
        this.workerResponseCount = workerResponseCount;
    }

    public DashboardSummaryCountsResponse getSummaryCounts() {
        return summaryCounts;
    }

    public List<DashboardTaskSummaryResponse> getPriorityTasks() {
        return priorityTasks;
    }

    public long getApprovalCount() {
        return approvalCount;
    }

    public long getWorkerResponseCount() {
        return workerResponseCount;
    }
}
