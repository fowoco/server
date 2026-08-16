package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import com.fowoco.server.worker.domain.WorkerDocument;
import com.fowoco.server.worker.domain.WorkerDocumentSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "DocumentItemResponse", description = "통합 문서함의 서류 항목 (근로자 표시 정보 포함)")
public final class DocumentItemResponse {

    @JsonProperty("worker_document_id")
    @Schema(name = "worker_document_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID workerDocumentId;

    @JsonProperty("worker_id")
    @Schema(name = "worker_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID workerId;

    @JsonProperty("task_id")
    @Schema(name = "task_id", format = "uuid", description = "연결된 업무카드 ID")
    private final UUID taskId;

    @JsonProperty("display_name")
    @Schema(
            name = "display_name",
            description = "근로자 화면 표시 이름. 근로자가 조회 시점에 삭제된 경우 null.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String displayName;

    @JsonProperty("document_type")
    @Schema(name = "document_type", requiredMode = Schema.RequiredMode.REQUIRED)
    private final DocumentType documentType;

    @JsonProperty("submission_status")
    @Schema(name = "submission_status", requiredMode = Schema.RequiredMode.REQUIRED)
    private final SubmissionStatus submissionStatus;

    @JsonProperty("source")
    @Schema(name = "source", description = "서류가 등록된 경로", requiredMode = Schema.RequiredMode.REQUIRED)
    private final WorkerDocumentSource source;

    @JsonProperty("expiry_date")
    @Schema(name = "expiry_date", format = "date")
    private final LocalDate expiryDate;

    @JsonProperty("file_id")
    @Schema(name = "file_id", format = "uuid")
    private final UUID fileId;

    @JsonProperty("version")
    @Schema(name = "version", description = "PATCH 요청의 expected_version 기준값", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long version;

    private DocumentItemResponse(
            UUID workerDocumentId,
            UUID workerId,
            UUID taskId,
            String displayName,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            WorkerDocumentSource source,
            LocalDate expiryDate,
            UUID fileId,
            long version
    ) {
        this.workerDocumentId = workerDocumentId;
        this.workerId = workerId;
        this.taskId = taskId;
        this.displayName = displayName;
        this.documentType = documentType;
        this.submissionStatus = submissionStatus;
        this.source = source;
        this.expiryDate = expiryDate;
        this.fileId = fileId;
        this.version = version;
    }

    public static DocumentItemResponse from(WorkerDocument document, String displayName) {
        return new DocumentItemResponse(
                document.workerDocumentId(),
                document.workerId(),
                document.taskId(),
                displayName,
                document.documentType(),
                document.submissionStatus(),
                document.source(),
                document.expiryDate(),
                document.fileId(),
                document.version()
        );
    }

    public UUID getWorkerDocumentId() {
        return workerDocumentId;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getWorkerDisplayName() {
        return displayName;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public SubmissionStatus getSubmissionStatus() {
        return submissionStatus;
    }

    public WorkerDocumentSource getSource() {
        return source;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public UUID getFileId() {
        return fileId;
    }

    public long getVersion() {
        return version;
    }
}
