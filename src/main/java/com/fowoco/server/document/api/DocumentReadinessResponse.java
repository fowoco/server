package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "DocumentReadinessResponse", description = "업무별 서류 준비도")
public final class DocumentReadinessResponse {

    @JsonProperty("required")
    @Schema(description = "이 업무에 필요한 전체 서류 종류", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<DocumentType> required;

    @JsonProperty("available")
    @Schema(description = "보유 중이고 유효한 서류 종류", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<DocumentType> available;

    @JsonProperty("missing")
    @Schema(description = "아예 등록되지 않은 서류 종류", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<DocumentType> missing;

    @JsonProperty("expired")
    @Schema(description = "등록됐지만 유효기간이 지난 서류 종류", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<DocumentType> expired;

    @JsonProperty("completion_blocked")
    @Schema(
            name = "completion_blocked",
            description = "missing 또는 expired가 하나라도 있으면 true",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final boolean completionBlocked;

    public DocumentReadinessResponse(
            List<DocumentType> required,
            List<DocumentType> available,
            List<DocumentType> missing,
            List<DocumentType> expired,
            boolean completionBlocked
    ) {
        this.required = required;
        this.available = available;
        this.missing = missing;
        this.expired = expired;
        this.completionBlocked = completionBlocked;
    }

    public List<DocumentType> getRequired() {
        return required;
    }

    public List<DocumentType> getAvailable() {
        return available;
    }

    public List<DocumentType> getMissing() {
        return missing;
    }

    public List<DocumentType> getExpired() {
        return expired;
    }

    public boolean isCompletionBlocked() {
        return completionBlocked;
    }
}
