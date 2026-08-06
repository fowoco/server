package com.fowoco.server.document.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.document.application.DocumentDetailResult;
import com.fowoco.server.file.domain.ScanStatus;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "DocumentDetailResponse", description = "서류 단건 상세 응답 (연결된 파일 정보 포함)")
public final class DocumentDetailResponse {

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
    @Schema(name = "display_name", description = "근로자 화면 표시 이름")
    private final String displayName;

    @JsonProperty("document_type")
    @Schema(name = "document_type", requiredMode = Schema.RequiredMode.REQUIRED)
    private final DocumentType documentType;

    @JsonProperty("submission_status")
    @Schema(name = "submission_status", requiredMode = Schema.RequiredMode.REQUIRED)
    private final SubmissionStatus submissionStatus;

    @JsonProperty("expiry_date")
    @Schema(name = "expiry_date", format = "date")
    private final LocalDate expiryDate;

    @JsonProperty("version")
    @Schema(name = "version", description = "PATCH 요청의 expected_version 기준값", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long version;

    @JsonProperty("file_id")
    @Schema(name = "file_id", format = "uuid")
    private final UUID fileId;

    @JsonProperty("file_name")
    @Schema(name = "file_name", description = "연결된 파일의 표시 파일명 (파일 없으면 null)")
    private final String fileName;

    @JsonProperty("file_mime_type")
    @Schema(name = "file_mime_type", description = "연결된 파일의 MIME 타입 (파일 없으면 null)")
    private final String fileMimeType;

    @JsonProperty("file_size")
    @Schema(name = "file_size", description = "연결된 파일의 크기(byte) (파일 없으면 null)")
    private final Long fileSize;

    @JsonProperty("file_scan_status")
    @Schema(name = "file_scan_status", description = "연결된 파일의 검사 상태 (파일 없으면 null)")
    private final ScanStatus fileScanStatus;

    private DocumentDetailResponse(
            UUID workerDocumentId,
            UUID workerId,
            UUID taskId,
            String displayName,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            LocalDate expiryDate,
            long version,
            UUID fileId,
            String fileName,
            String fileMimeType,
            Long fileSize,
            ScanStatus fileScanStatus
    ) {
        this.workerDocumentId = workerDocumentId;
        this.workerId = workerId;
        this.taskId = taskId;
        this.displayName = displayName;
        this.documentType = documentType;
        this.submissionStatus = submissionStatus;
        this.expiryDate = expiryDate;
        this.version = version;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileMimeType = fileMimeType;
        this.fileSize = fileSize;
        this.fileScanStatus = fileScanStatus;
    }

    public static DocumentDetailResponse from(DocumentDetailResult result) {
        var document = result.document();
        var storedFile = result.storedFile();
        return new DocumentDetailResponse(
                document.workerDocumentId(),
                document.workerId(),
                document.taskId(),
                result.workerDisplayName(),
                document.documentType(),
                document.submissionStatus(),
                document.expiryDate(),
                document.version(),
                document.fileId(),
                storedFile == null ? null : storedFile.name(),
                storedFile == null ? null : storedFile.mimeType(),
                storedFile == null ? null : storedFile.size(),
                storedFile == null ? null : storedFile.scanStatus()
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

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public long getVersion() {
        return version;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileMimeType() {
        return fileMimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public ScanStatus getFileScanStatus() {
        return fileScanStatus;
    }
}
