package com.fowoco.server.document.infrastructure.persistence;

import com.fowoco.server.document.domain.DocumentOcrRun;
import com.fowoco.server.document.domain.DocumentOcrRunStatus;
import com.fowoco.server.worker.domain.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "document_ocr_run")
public class DocumentOcrRunJpaEntity {

    @Id
    @Column(name = "ocr_run_id", nullable = false, updatable = false)
    private UUID ocrRunId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "worker_document_id", nullable = false, updatable = false)
    private UUID workerDocumentId;

    @Column(name = "stored_file_id", nullable = false, updatable = false)
    private UUID storedFileId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "runtime_request_id", nullable = false, updatable = false)
    private UUID runtimeRequestId;

    @Column(name = "idempotency_key_hash", nullable = false, updatable = false, length = 64)
    private String idempotencyKeyHash;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 40)
    private DocumentType documentType;

    @Column(name = "country_code", updatable = false, length = 3)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DocumentOcrRunStatus status;

    @Column(name = "result_ciphertext", columnDefinition = "TEXT")
    private String resultCiphertext;

    @Column(name = "result_key_version", length = 60)
    private String resultKeyVersion;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_reason", length = 300)
    private String reviewReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DocumentOcrRunJpaEntity() {
    }

    private DocumentOcrRunJpaEntity(DocumentOcrRun run) {
        this.ocrRunId = run.ocrRunId();
        this.companyId = run.companyId();
        this.workerDocumentId = run.workerDocumentId();
        this.storedFileId = run.storedFileId();
        this.requestedBy = run.requestedBy();
        this.runtimeRequestId = run.runtimeRequestId();
        this.idempotencyKeyHash = run.idempotencyKeyHash();
        this.requestHash = run.requestHash();
        this.documentType = run.documentType();
        this.countryCode = run.countryCode();
        applyMutableState(run);
        this.createdAt = run.createdAt();
        this.version = run.version();
    }

    public static DocumentOcrRunJpaEntity fromDomain(DocumentOcrRun run) {
        return new DocumentOcrRunJpaEntity(Objects.requireNonNull(run, "run must not be null"));
    }

    public void applyState(DocumentOcrRun run) {
        Objects.requireNonNull(run, "run must not be null");
        if (!ocrRunId.equals(run.ocrRunId())
                || !companyId.equals(run.companyId())
                || !workerDocumentId.equals(run.workerDocumentId())
                || !storedFileId.equals(run.storedFileId())
                || !runtimeRequestId.equals(run.runtimeRequestId())
                || version != run.version()) {
            throw new IllegalArgumentException("immutable OCR run fields or version do not match");
        }
        applyMutableState(run);
    }

    private void applyMutableState(DocumentOcrRun run) {
        this.status = run.status();
        this.resultCiphertext = run.resultCiphertext();
        this.resultKeyVersion = run.resultKeyVersion();
        this.lastErrorCode = run.lastErrorCode();
        this.reviewedBy = run.reviewedBy();
        this.reviewReason = run.reviewReason();
        this.startedAt = run.startedAt();
        this.completedAt = run.completedAt();
        this.reviewedAt = run.reviewedAt();
        this.updatedAt = run.updatedAt();
    }

    public DocumentOcrRun toDomain() {
        return new DocumentOcrRun(
                ocrRunId, companyId, workerDocumentId, storedFileId, requestedBy,
                runtimeRequestId, idempotencyKeyHash, requestHash, documentType,
                countryCode, status, resultCiphertext, resultKeyVersion, lastErrorCode,
                reviewedBy, reviewReason, createdAt, startedAt, completedAt, reviewedAt,
                updatedAt, version
        );
    }
}
