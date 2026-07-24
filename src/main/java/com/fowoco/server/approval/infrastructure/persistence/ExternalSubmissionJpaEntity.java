package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.domain.ExternalSubmission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_submission")
class ExternalSubmissionJpaEntity {

    @Id
    @Column(name = "external_submission_id", nullable = false, updatable = false)
    private UUID externalSubmissionId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "destination", nullable = false, length = 160, updatable = false)
    private String destination;
    @Column(name = "safe_reference", nullable = false, length = 300, updatable = false)
    private String safeReference;
    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExternalSubmissionJpaEntity() {
    }

    ExternalSubmissionJpaEntity(ExternalSubmission submission) {
        this.externalSubmissionId = submission.externalSubmissionId();
        this.taskId = submission.taskId();
        this.companyId = submission.companyId();
        this.destination = submission.destination();
        this.safeReference = submission.safeReference();
        this.submittedBy = submission.submittedBy();
        this.submittedAt = submission.submittedAt();
        this.createdAt = submission.createdAt();
    }

    ExternalSubmission toDomain() {
        return new ExternalSubmission(
                externalSubmissionId,
                taskId,
                companyId,
                destination,
                safeReference,
                submittedBy,
                submittedAt,
                createdAt
        );
    }
}
