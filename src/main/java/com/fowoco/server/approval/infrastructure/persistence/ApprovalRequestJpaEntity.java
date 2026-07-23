package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.approval.domain.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_request")
class ApprovalRequestJpaEntity {

    @Id
    @Column(name = "approval_request_id", nullable = false, updatable = false)
    private UUID approvalRequestId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "target_task_version", nullable = false, updatable = false)
    private long targetTaskVersion;
    @Column(name = "approved_task_version")
    private Long approvedTaskVersion;
    @Column(name = "target_fingerprint", nullable = false, length = 64, updatable = false)
    private String targetFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStatus status;
    @Column(name = "ai_snapshot_json", columnDefinition = "TEXT", updatable = false)
    private String aiSnapshotJson;
    @Column(name = "hr_snapshot_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String hrSnapshotJson;
    @Column(name = "changed_fields_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String changedFieldsJson;
    @Column(name = "source_versions_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String sourceVersionsJson;
    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "decision_reason", length = 500)
    private String decisionReason;
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;
    @Column(name = "invalidation_reason", length = 500)
    private String invalidationReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ApprovalRequestJpaEntity() {
    }

    ApprovalRequestJpaEntity(ApprovalRequest request) {
        this.approvalRequestId = request.approvalRequestId();
        this.taskId = request.taskId();
        this.companyId = request.companyId();
        this.targetTaskVersion = request.targetTaskVersion();
        this.targetFingerprint = request.targetFingerprint();
        this.aiSnapshotJson = request.aiSnapshotJson();
        this.hrSnapshotJson = request.hrSnapshotJson();
        this.changedFieldsJson = request.changedFieldsJson();
        this.sourceVersionsJson = request.sourceVersionsJson();
        this.requestedBy = request.requestedBy();
        this.requestedAt = request.requestedAt();
        this.createdAt = request.createdAt();
        apply(request);
        this.version = request.version();
    }

    void apply(ApprovalRequest request) {
        this.approvedTaskVersion = request.approvedTaskVersion();
        this.status = request.status();
        this.decidedBy = request.decidedBy();
        this.decidedAt = request.decidedAt();
        this.decisionReason = request.decisionReason();
        this.invalidatedAt = request.invalidatedAt();
        this.invalidationReason = request.invalidationReason();
        this.updatedAt = request.updatedAt();
    }

    ApprovalRequest toDomain() {
        return new ApprovalRequest(
                approvalRequestId,
                taskId,
                companyId,
                targetTaskVersion,
                approvedTaskVersion,
                targetFingerprint,
                status,
                aiSnapshotJson,
                hrSnapshotJson,
                changedFieldsJson,
                sourceVersionsJson,
                requestedBy,
                requestedAt,
                decidedBy,
                decidedAt,
                decisionReason,
                invalidatedAt,
                invalidationReason,
                createdAt,
                updatedAt,
                version
        );
    }
}
