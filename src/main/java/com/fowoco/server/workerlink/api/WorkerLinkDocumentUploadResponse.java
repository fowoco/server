package com.fowoco.server.workerlink.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.file.domain.StoredFile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "WorkerLinkDocumentUploadResponse", description = "근로자 링크 문서 업로드 결과")
public final class WorkerLinkDocumentUploadResponse {

    @JsonProperty("upload_id")
    @Schema(name = "upload_id")
    private final UUID uploadId;

    @JsonProperty("file_name")
    @Schema(name = "file_name")
    private final String fileName;

    @JsonProperty("size")
    private final long size;

    @JsonProperty("expires_at")
    @Schema(name = "expires_at")
    private final Instant expiresAt;

    private WorkerLinkDocumentUploadResponse(UUID uploadId, String fileName, long size, Instant expiresAt) {
        this.uploadId = uploadId;
        this.fileName = fileName;
        this.size = size;
        this.expiresAt = expiresAt;
    }

    public static WorkerLinkDocumentUploadResponse from(StoredFile storedFile, Instant linkExpiresAt) {
        return new WorkerLinkDocumentUploadResponse(
                storedFile.storedFileId(),
                storedFile.name(),
                storedFile.size(),
                linkExpiresAt
        );
    }

    public UUID getUploadId() {
        return uploadId;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
