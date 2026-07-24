package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.domain.Evidence;
import com.fowoco.server.approval.domain.EvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_evidence")
class EvidenceJpaEntity {

    @Id
    @Column(name = "evidence_id", nullable = false, updatable = false)
    private UUID evidenceId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30, updatable = false)
    private EvidenceType evidenceType;
    @Column(name = "file_reference", length = 300, updatable = false)
    private String fileReference;
    @Column(name = "note", length = 500, updatable = false)
    private String note;
    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvidenceJpaEntity() {
    }

    EvidenceJpaEntity(Evidence evidence) {
        this.evidenceId = evidence.evidenceId();
        this.taskId = evidence.taskId();
        this.companyId = evidence.companyId();
        this.evidenceType = evidence.evidenceType();
        this.fileReference = evidence.fileReference();
        this.note = evidence.note();
        this.recordedBy = evidence.recordedBy();
        this.recordedAt = evidence.recordedAt();
        this.createdAt = evidence.createdAt();
    }

    Evidence toDomain() {
        return new Evidence(
                evidenceId,
                taskId,
                companyId,
                evidenceType,
                fileReference,
                note,
                recordedBy,
                recordedAt,
                createdAt
        );
    }
}
