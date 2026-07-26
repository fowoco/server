package com.fowoco.server.document.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StoredFile {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_MIME_TYPE_LENGTH = 127;
    private static final int MAX_PURPOSE_LENGTH = 60;

    private final UUID storedFileId;
    private final UUID companyId;
    private final String name;
    private final String mimeType;
    private final long size;
    private final String purpose;
    private final UUID taskId;
    private final UUID workerId;
    private final String storageKey;
    private final ScanStatus scanStatus;
    private final Instant createdAt;

    public StoredFile(
            UUID storedFileId,
            UUID companyId,
            String name,
            String mimeType,
            long size,
            String purpose,
            UUID taskId,
            UUID workerId,
            String storageKey,
            ScanStatus scanStatus,
            Instant createdAt
    ) {
        this.storedFileId = Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.name = requireBounded(name, MAX_NAME_LENGTH, "name");
        this.mimeType = requireBounded(mimeType, MAX_MIME_TYPE_LENGTH, "mimeType");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        this.size = size;
        this.purpose = requireBounded(purpose, MAX_PURPOSE_LENGTH, "purpose");
        this.taskId = taskId;
        this.workerId = workerId;
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey must not be null");
        this.scanStatus = Objects.requireNonNull(scanStatus, "scanStatus must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * 파일 업로드 등록. storageKey는 원본 파일명과 무관하게 서버가 생성한 값을 넘겨받는다
     * (파일명으로 저장 경로를 만들지 않는다는 #13 보안 규칙).
     * scanStatus는 검사 인프라가 없는 지금은 항상 NOT_SCANNED로 고정한다.
     */
    public static StoredFile create(
            UUID storedFileId,
            UUID companyId,
            String name,
            String mimeType,
            long size,
            String purpose,
            UUID taskId,
            UUID workerId,
            String storageKey,
            Instant now
    ) {
        return new StoredFile(
                storedFileId,
                companyId,
                name,
                mimeType,
                size,
                purpose,
                taskId,
                workerId,
                storageKey,
                ScanStatus.NOT_SCANNED,
                now
        );
    }

    private static String requireBounded(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    public UUID storedFileId() {
        return storedFileId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public String mimeType() {
        return mimeType;
    }

    public long size() {
        return size;
    }

    public String purpose() {
        return purpose;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID workerId() {
        return workerId;
    }

    public String storageKey() {
        return storageKey;
    }

    public ScanStatus scanStatus() {
        return scanStatus;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
