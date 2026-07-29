package com.fowoco.server.file.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.file.domain.ScanStatus;
import com.fowoco.server.file.domain.StoredFile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "FileUploadResponse", description = "파일 업로드 응답")
public final class FileUploadResponse {

    @JsonProperty("file_id")
    @Schema(name = "file_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID fileId;

    @JsonProperty("name")
    @Schema(description = "원본 파일명", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String name;

    @JsonProperty("mime_type")
    @Schema(name = "mime_type", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String mimeType;

    @JsonProperty("size")
    @Schema(description = "파일 크기(byte)", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long size;

    @JsonProperty("scan_status")
    @Schema(
            name = "scan_status",
            description = "악성파일 검사 상태. 현재는 검사 인프라 미도입으로 항상 NOT_SCANNED.",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final ScanStatus scanStatus;

    private FileUploadResponse(UUID fileId, String name, String mimeType, long size, ScanStatus scanStatus) {
        this.fileId = fileId;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
        this.scanStatus = scanStatus;
    }

    public static FileUploadResponse from(StoredFile storedFile) {
        return new FileUploadResponse(
                storedFile.storedFileId(),
                storedFile.name(),
                storedFile.mimeType(),
                storedFile.size(),
                storedFile.scanStatus()
        );
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getName() {
        return name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public ScanStatus getScanStatus() {
        return scanStatus;
    }
}
