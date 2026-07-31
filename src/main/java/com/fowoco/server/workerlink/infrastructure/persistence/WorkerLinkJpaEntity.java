package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.domain.ConversationStatus;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerLinkStatus;
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
@Table(name = "worker_link")
public class WorkerLinkJpaEntity {

    @Id
    @Column(name = "worker_link_id", nullable = false, updatable = false)
    private UUID workerLinkId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkerLinkStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_status", nullable = false, length = 20)
    private ConversationStatus conversationStatus;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(name = "replaces_link_id", updatable = false)
    private UUID replacesLinkId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkerLinkJpaEntity() {
    }

    private WorkerLinkJpaEntity(
            UUID workerLinkId,
            UUID taskId,
            UUID companyId,
            String tokenHash,
            Instant expiresAt,
            WorkerLinkStatus status,
            ConversationStatus conversationStatus,
            UUID assigneeId,
            UUID issuedBy,
            UUID replacesLinkId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.workerLinkId = workerLinkId;
        this.taskId = taskId;
        this.companyId = companyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.status = status;
        this.conversationStatus = conversationStatus;
        this.assigneeId = assigneeId;
        this.issuedBy = issuedBy;
        this.replacesLinkId = replacesLinkId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static WorkerLinkJpaEntity fromDomain(WorkerLink workerLink) {
        Objects.requireNonNull(workerLink, "workerLink must not be null");
        return new WorkerLinkJpaEntity(
                workerLink.workerLinkId(),
                workerLink.taskId(),
                workerLink.companyId(),
                workerLink.tokenHash(),
                workerLink.expiresAt(),
                workerLink.status(),
                workerLink.conversationStatus(),
                workerLink.assigneeId(),
                workerLink.issuedBy(),
                workerLink.replacesLinkId(),
                workerLink.createdAt(),
                workerLink.updatedAt(),
                workerLink.version()
        );
    }

    public WorkerLink toDomain() {
        return new WorkerLink(
                workerLinkId,
                taskId,
                companyId,
                tokenHash,
                expiresAt,
                status,
                conversationStatus,
                assigneeId,
                issuedBy,
                replacesLinkId,
                createdAt,
                updatedAt,
                version
        );
    }

    public void applyState(WorkerLink workerLink) {
        Objects.requireNonNull(workerLink, "workerLink must not be null");
        if (!workerLinkId.equals(workerLink.workerLinkId())
                || !taskId.equals(workerLink.taskId())
                || !companyId.equals(workerLink.companyId())
                || !tokenHash.equals(workerLink.tokenHash())
                || !createdAt.equals(workerLink.createdAt())) {
            throw new IllegalArgumentException("immutable worker link fields must not change");
        }
        if (version != workerLink.version()) {
            throw new IllegalArgumentException("worker link version does not match");
        }
        this.status = workerLink.status();
        this.conversationStatus = workerLink.conversationStatus();
        this.assigneeId = workerLink.assigneeId();
        this.updatedAt = workerLink.updatedAt();
    }
}
