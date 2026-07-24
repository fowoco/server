package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import com.fowoco.server.worker.domain.WorkerDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "worker_document")
public class WorkerDocumentJpaEntity {

    @Id
    @Column(name = "worker_document_id", nullable = false, updatable = false)
    private UUID workerDocumentId;

    @Column(name = "worker_id", nullable = false, updatable = false)
    private UUID workerId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_status", nullable = false, length = 20)
    private SubmissionStatus submissionStatus;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "destination", length = 120)
    private String destination;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkerDocumentJpaEntity() {
    }

    private WorkerDocumentJpaEntity(
            UUID workerDocumentId,
            UUID workerId,
            UUID companyId,
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
        this.companyId = companyId;
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

    public static WorkerDocumentJpaEntity fromDomain(WorkerDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        return new WorkerDocumentJpaEntity(
                document.workerDocumentId(),
                document.workerId(),
                document.companyId(),
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

    public WorkerDocument toDomain() {
        return new WorkerDocument(
                workerDocumentId,
                workerId,
                companyId,
                documentType,
                submissionStatus,
                expiryDate,
                destination,
                note,
                fileId,
                createdAt,
                updatedAt,
                version
        );
    }

    public void applyState(WorkerDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        if (!workerDocumentId.equals(document.workerDocumentId())
                || !workerId.equals(document.workerId())
                || !companyId.equals(document.companyId())
                || !createdAt.equals(document.createdAt())) {
            throw new IllegalArgumentException("immutable worker document fields must not change");
        }
        if (version != document.version()) {
            throw new IllegalArgumentException("worker document version does not match");
        }
        this.documentType = document.documentType();
        this.submissionStatus = document.submissionStatus();
        this.expiryDate = document.expiryDate();
        this.destination = document.destination();
        this.note = document.note();
        this.fileId = document.fileId();
        this.updatedAt = document.updatedAt();
    }
}
