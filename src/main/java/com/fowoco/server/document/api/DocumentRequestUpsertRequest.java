package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(name = "DocumentRequestUpsertRequest", description = "업무별 문서 요청 초안 저장·갱신 요청")
public final class DocumentRequestUpsertRequest {

    @Schema(description = "안내 언어", example = "vi", maxLength = 20, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "language를 입력해 주세요.")
    @Size(max = 20, message = "language는 20자 이하여야 합니다.")
    private final String language;

    @Schema(name = "document_types", description = "요청할 서류 종류 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "document_types는 최소 1개 이상이어야 합니다.")
    private final List<DocumentType> documentTypes;

    @Schema(description = "근로자에게 보여줄 안내 메시지", maxLength = 1000)
    @Size(max = 1000, message = "message는 1000자 이하여야 합니다.")
    private final String message;

    @Schema(
            name = "expected_version",
            description = "최초 생성 시에는 0을 보냅니다.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final Long expectedVersion;

    @JsonCreator
    public DocumentRequestUpsertRequest(
            @JsonProperty("language") String language,
            @JsonProperty("document_types") List<DocumentType> documentTypes,
            @JsonProperty("message") String message,
            @JsonProperty("expected_version") Long expectedVersion
    ) {
        this.language = language;
        this.documentTypes = documentTypes;
        this.message = message;
        this.expectedVersion = expectedVersion;
    }

    public String getLanguage() {
        return language;
    }

    public List<DocumentType> getDocumentTypes() {
        return documentTypes;
    }

    public String getMessage() {
        return message;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }
}
