package com.fowoco.server.dashboard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "DashboardRecommendationsResponse", description = "Agent가 준비한 내용 묶음")
public final class DashboardRecommendationsResponse {

    @JsonProperty("connected_count")
    @Schema(name = "connected_count", description = "연결된 전체 열린 업무 개수")
    private final long connectedCount;

    @JsonProperty("prepared")
    @Schema(name = "prepared", description = "Agent가 생성한 초안 (AI_CANDIDATE 소스, DRAFT 상태)")
    private final List<DashboardRecommendationItemResponse> prepared;

    @JsonProperty("review")
    @Schema(name = "review", description = "담당자 확인 필요 (NEEDS_INFO 또는 READY_FOR_REVIEW)")
    private final List<DashboardRecommendationItemResponse> review;

    @JsonProperty("after_approval")
    @Schema(name = "after_approval", description = "응답·기관 대기 (WAITING_WORKER 또는 WAITING_EXTERNAL)")
    private final List<DashboardRecommendationItemResponse> afterApproval;

    public DashboardRecommendationsResponse(
            long connectedCount,
            List<DashboardRecommendationItemResponse> prepared,
            List<DashboardRecommendationItemResponse> review,
            List<DashboardRecommendationItemResponse> afterApproval
    ) {
        this.connectedCount = connectedCount;
        this.prepared = prepared;
        this.review = review;
        this.afterApproval = afterApproval;
    }

    public long getConnectedCount() {
        return connectedCount;
    }

    public List<DashboardRecommendationItemResponse> getPrepared() {
        return prepared;
    }

    public List<DashboardRecommendationItemResponse> getReview() {
        return review;
    }

    public List<DashboardRecommendationItemResponse> getAfterApproval() {
        return afterApproval;
    }
}
