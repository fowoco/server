package com.fowoco.server.file.infrastructure.persistence;

import com.fowoco.server.file.domain.ScanStatus;
import com.fowoco.server.file.domain.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stored_file")
public class StoredFileJpaEntity {

    @Id
    @Column(name = "stored_file_id", nullable = false, updatable = false)
    private UUID storedFileId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "purpose", nullable = false, length = 60)
    private String purpose;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "worker_id")
    private UUID workerId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 255)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 20)
    private ScanStatus scanStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoredFileJpaEntity() {
    }

    private StoredFileJpaEntity(
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
        this.storedFileId = storedFileId;
        this.companyId = companyId;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
        this.purpose = purpose;
        this.taskId = taskId;
        this.workerId = workerId;
        this.storageKey = storageKey;
        this.scanStatus = scanStatus;
        this.createdAt = createdAt;
    }

    public static StoredFileJpaEntity fromDomain(StoredFile storedFile) {
        Objects.requireNonNull(storedFile, "storedFile must not be null");
        return new StoredFileJpaEntity(
                storedFile.storedFileId(),
                storedFile.companyId(),
                storedFile.name(),
                storedFile.mimeType(),
                storedFile.size(),
                storedFile.purpose(),
                storedFile.taskId(),
                storedFile.workerId(),
                storedFile.storageKey(),
                storedFile.scanStatus(),
                storedFile.createdAt()
        );
    }

    public StoredFile toDomain() {
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
                scanStatus,
                createdAt
        );
    }
}
