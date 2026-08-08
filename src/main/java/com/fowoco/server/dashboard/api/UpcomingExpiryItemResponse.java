package com.fowoco.server.dashboard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "UpcomingExpiryItemResponse", description = "7일 이내 체류·계약·서류 만료 항목")
public final class UpcomingExpiryItemResponse {

    @JsonProperty("worker_id")
    @Schema(name = "worker_id", format = "uuid")
    private final UUID workerId;

    @JsonProperty("display_name")
    @Schema(name = "display_name")
    private final String displayName;

    @JsonProperty("category")
    @Schema(name = "category", description = "만료 종류")
    private final UpcomingExpiryCategory category;

    @JsonProperty("expiry_date")
    @Schema(name = "expiry_date", format = "date")
    private final LocalDate expiryDate;

    @JsonProperty("document_type")
    @Schema(name = "document_type", description = "category가 DOCUMENT_EXPIRY일 때만 값이 있음")
    private final DocumentType documentType;

    public UpcomingExpiryItemResponse(
            UUID workerId,
            String displayName,
            UpcomingExpiryCategory category,
            LocalDate expiryDate,
            DocumentType documentType
    ) {
        this.workerId = workerId;
        this.displayName = displayName;
        this.category = category;
        this.expiryDate = expiryDate;
        this.documentType = documentType;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UpcomingExpiryCategory getCategory() {
        return category;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }
}
