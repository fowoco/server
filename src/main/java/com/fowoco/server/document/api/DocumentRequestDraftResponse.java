package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.document.domain.DocumentRequestReviewStatus;
import com.fowoco.server.worker.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "DocumentRequestDraftResponse", description = "업무별 문서 요청 초안 저장 결과")
public final class DocumentRequestDraftResponse {

    @JsonProperty("draft_id")
    @Schema(name = "draft_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID draftId;

    @JsonProperty("language")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "vi")
    private final String language;

    @JsonProperty("document_types")
    @Schema(name = "document_types", requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<DocumentType> documentTypes;

    @JsonProperty("message")
    @Schema(description = "근로자에게 표시할 요청 안내문", nullable = true)
    private final String message;

    @JsonProperty("version")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final long version;

    @JsonProperty("review_status")
    @Schema(name = "review_status", requiredMode = Schema.RequiredMode.REQUIRED)
    private final DocumentRequestReviewStatus reviewStatus;

    @JsonProperty("updated_at")
    @Schema(name = "updated_at", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant updatedAt;

    private DocumentRequestDraftResponse(
            UUID draftId,
            String language,
            List<DocumentType> documentTypes,
            String message,
            long version,
            DocumentRequestReviewStatus reviewStatus,
            Instant updatedAt
    ) {
        this.draftId = draftId;
        this.language = language;
        this.documentTypes = List.copyOf(documentTypes);
        this.message = message;
        this.version = version;
        this.reviewStatus = reviewStatus;
        this.updatedAt = updatedAt;
    }

    public static DocumentRequestDraftResponse from(DocumentRequestDraft draft) {
        return new DocumentRequestDraftResponse(
                draft.draftId(),
                draft.language(),
                draft.documentTypes(),
                draft.message(),
                draft.version(),
                draft.reviewStatus(),
                draft.updatedAt()
        );
    }

    public UUID getDraftId() {
        return draftId;
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

    public long getVersion() {
        return version;
    }

    public DocumentRequestReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
