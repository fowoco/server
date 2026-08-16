package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(name = "WorkerResponseSubmitRequest", description = "근로자 응답 제출 요청")
public final class WorkerResponseSubmitRequest {

    @Schema(name = "response_type", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "response_type을 입력해 주세요.")
    private final WorkerResponseType responseType;

    @Schema(description = "근로자 메시지", maxLength = 1000)
    @Size(max = 1000, message = "message는 1000자 이하여야 합니다.")
    private final String message;

    @Schema(name = "upload_ids", description = "함께 제출할 업로드 ID 목록")
    private final List<UUID> uploadIds;

    @Schema(description = "Server가 requested_actions로 요청한 구조화 Slot 답변")
    @Size(max = 20, message = "answers는 20개 이하여야 합니다.")
    private final Map<String, String> answers;

    @Schema(name = "idempotency_key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "idempotency_key를 입력해 주세요.")
    @Size(max = 100, message = "idempotency_key는 100자 이하여야 합니다.")
    private final String idempotencyKey;

    @JsonCreator
    public WorkerResponseSubmitRequest(
            @JsonProperty("response_type") WorkerResponseType responseType,
            @JsonProperty("message") String message,
            @JsonProperty("upload_ids") List<UUID> uploadIds,
            @JsonProperty("answers") Map<String, String> answers,
            @JsonProperty("idempotency_key") String idempotencyKey
    ) {
        this.responseType = responseType;
        this.message = message;
        this.uploadIds = uploadIds;
        this.answers = answers;
        this.idempotencyKey = idempotencyKey;
    }

    public WorkerResponseType getResponseType() {
        return responseType;
    }

    public String getMessage() {
        return message;
    }

    public List<UUID> getUploadIds() {
        return uploadIds;
    }

    public Map<String, String> getAnswers() {
        return answers;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
