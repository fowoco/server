package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.workerlink.application.WorkerLinkViewResult;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "WorkerLinkViewResponse", description = "근로자 공개 안내 조회 결과")
public final class WorkerLinkViewResponse {

    @JsonProperty("guidance")
    @Schema(description = "번역된 안내 내용")
    private final String guidance;

    @JsonProperty("due_date")
    @Schema(name = "due_date", description = "제출 마감일")
    private final LocalDate dueDate;

    @JsonProperty("allowed_responses")
    @Schema(name = "allowed_responses", description = "이 링크에서 허용되는 응답 유형")
    private final List<WorkerResponseType> allowedResponses;

    private WorkerLinkViewResponse(String guidance, LocalDate dueDate, List<WorkerResponseType> allowedResponses) {
        this.guidance = guidance;
        this.dueDate = dueDate;
        this.allowedResponses = allowedResponses;
    }

    public static WorkerLinkViewResponse from(WorkerLinkViewResult result) {
        return new WorkerLinkViewResponse(result.guidance(), result.dueDate(), result.allowedResponses());
    }

    public String getGuidance() {
        return guidance;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public List<WorkerResponseType> getAllowedResponses() {
        return allowedResponses;
    }
}
