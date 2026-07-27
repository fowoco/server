package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.document.domain.DocumentRequestReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "DocumentRequestDraftResponse", description = "업무별 문서 요청 초안 저장 결과")
public final class DocumentRequestDraftResponse {

    @JsonProperty("draft_id")
    @Schema(name = "draft_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID draftId;

    @JsonProperty("version")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final long version;

    @JsonProperty("review_status")
    @Schema(name = "review_status", requiredMode = Schema.RequiredMode.REQUIRED)
    private final DocumentRequestReviewStatus reviewStatus;

    private DocumentRequestDraftResponse(UUID draftId, long version, DocumentRequestReviewStatus reviewStatus) {
        this.draftId = draftId;
        this.version = version;
        this.reviewStatus = reviewStatus;
    }

    public static DocumentRequestDraftResponse from(DocumentRequestDraft draft) {
        return new DocumentRequestDraftResponse(draft.draftId(), draft.version(), draft.reviewStatus());
    }

    public UUID getDraftId() {
        return draftId;
    }

    public long getVersion() {
        return version;
    }

    public DocumentRequestReviewStatus getReviewStatus() {
        return reviewStatus;
    }
}
