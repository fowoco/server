package com.fowoco.server.worker.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import com.fowoco.server.worker.domain.WorkerDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "WorkerDocumentResponse", description = "근로자 서류 상태 응답")
public final class WorkerDocumentResponse {

    @JsonProperty("worker_document_id")
    @Schema(name = "worker_document_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID workerDocumentId;

    @JsonProperty("worker_id")
    @Schema(name = "worker_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID workerId;

    @JsonProperty("document_type")
    @Schema(name = "document_type", requiredMode = Schema.RequiredMode.REQUIRED)
    private final DocumentType documentType;

    @JsonProperty("submission_status")
    @Schema(name = "submission_status", requiredMode = Schema.RequiredMode.REQUIRED)
    private final SubmissionStatus submissionStatus;

    @JsonProperty("expiry_date")
    @Schema(name = "expiry_date", format = "date")
    private final LocalDate expiryDate;

    @JsonProperty("destination")
    private final String destination;

    @JsonProperty("note")
    private final String note;

    @JsonProperty("file_id")
    @Schema(name = "file_id", format = "uuid")
    private final UUID fileId;

    @JsonProperty("created_at")
    @Schema(name = "created_at", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant createdAt;

    @JsonProperty("updated_at")
    @Schema(name = "updated_at", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant updatedAt;

    @JsonProperty("version")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final long version;

    private WorkerDocumentResponse(
            UUID workerDocumentId,
            UUID workerId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            LocalDate expiryDate,
            String destination,
            String note,
            UUID fileId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.workerDocumentId = workerDocumentId;
        this.workerId = workerId;
        this.documentType = documentType;
        this.submissionStatus = submissionStatus;
        this.expiryDate = expiryDate;
        this.destination = destination;
        this.note = note;
        this.fileId = fileId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static WorkerDocumentResponse from(WorkerDocument document) {
        return new WorkerDocumentResponse(
                document.workerDocumentId(),
                document.workerId(),
                document.documentType(),
                document.submissionStatus(),
                document.expiryDate(),
                document.destination(),
                document.note(),
                document.fileId(),
                document.createdAt(),
                document.updatedAt(),
                document.version()
        );
    }

    public UUID getWorkerDocumentId() {
        return workerDocumentId;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public SubmissionStatus getSubmissionStatus() {
        return submissionStatus;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getDestination() {
        return destination;
    }

    public String getNote() {
        return note;
    }

    public UUID getFileId() {
        return fileId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
