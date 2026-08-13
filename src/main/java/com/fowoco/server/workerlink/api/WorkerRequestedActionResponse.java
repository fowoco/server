package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.workerlink.application.WorkerRequestedAction;
import com.fowoco.server.workerlink.application.WorkerRequestedActionInputType;
import com.fowoco.server.workerlink.application.WorkerRequestedActionType;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "WorkerRequestedActionResponse", description = "근로자 모바일 화면에서 수행할 작업")
public record WorkerRequestedActionResponse(
        @Schema(description = "작업 유형")
        WorkerRequestedActionType type,
        @JsonProperty("field_key")
        @Schema(name = "field_key", description = "구조화 답변 Slot key")
        String fieldKey,
        @Schema(description = "근로자에게 표시할 설명")
        String label,
        @JsonProperty("input_type")
        @Schema(name = "input_type", description = "입력 UI 유형")
        WorkerRequestedActionInputType inputType,
        @Schema(description = "필수 수행 여부")
        boolean required,
        @JsonProperty("document_type")
        @Schema(name = "document_type", description = "제출할 서류 유형")
        DocumentType documentType
) {

    static WorkerRequestedActionResponse from(WorkerRequestedAction action) {
        return new WorkerRequestedActionResponse(
                action.type(),
                action.fieldKey(),
                action.label(),
                action.inputType(),
                action.required(),
                action.documentType()
        );
    }
}
