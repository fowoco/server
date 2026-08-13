package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
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

    @JsonProperty("language")
    @Schema(description = "안내문 언어 코드", example = "vi")
    private final String language;

    @JsonProperty("due_date")
    @Schema(name = "due_date", description = "제출 마감일")
    private final LocalDate dueDate;

    @JsonProperty("requested_document_types")
    @Schema(name = "requested_document_types", description = "근로자에게 요청한 서류 유형")
    private final List<DocumentType> requestedDocumentTypes;

    @JsonProperty("allowed_responses")
    @Schema(name = "allowed_responses", description = "이 링크에서 허용되는 응답 유형")
    private final List<WorkerResponseType> allowedResponses;

    @JsonProperty("requested_actions")
    @Schema(name = "requested_actions", description = "모바일 화면에 표시할 구조화 작업")
    private final List<WorkerRequestedActionResponse> requestedActions;

    private WorkerLinkViewResponse(
            String guidance,
            String language,
            LocalDate dueDate,
            List<DocumentType> requestedDocumentTypes,
            List<WorkerResponseType> allowedResponses,
            List<WorkerRequestedActionResponse> requestedActions
    ) {
        this.guidance = guidance;
        this.language = language;
        this.dueDate = dueDate;
        this.requestedDocumentTypes = List.copyOf(requestedDocumentTypes);
        this.allowedResponses = List.copyOf(allowedResponses);
        this.requestedActions = List.copyOf(requestedActions);
    }

    public static WorkerLinkViewResponse from(WorkerLinkViewResult result) {
        return new WorkerLinkViewResponse(
                result.guidance(),
                result.language(),
                result.dueDate(),
                result.requestedDocumentTypes(),
                result.allowedResponses(),
                result.requestedActions().stream().map(WorkerRequestedActionResponse::from).toList()
        );
    }

    public String getGuidance() {
        return guidance;
    }

    public String getLanguage() {
        return language;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public List<DocumentType> getRequestedDocumentTypes() {
        return requestedDocumentTypes;
    }

    public List<WorkerResponseType> getAllowedResponses() {
        return allowedResponses;
    }

    public List<WorkerRequestedActionResponse> getRequestedActions() {
        return requestedActions;
    }
}
