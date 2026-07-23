package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_transition_history")
class TaskTransitionJpaEntity {

    @Id
    @Column(name = "transition_id", nullable = false, updatable = false)
    private UUID transitionId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false)
    private TaskStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private TaskStatus toStatus;
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;
    @Column(name = "reason", length = 500, updatable = false)
    private String reason;
    @Column(name = "request_id", nullable = false, length = 128, updatable = false)
    private String requestId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskTransitionJpaEntity() {
    }

    TaskTransitionJpaEntity(
            UUID transitionId,
            UUID taskId,
            UUID companyId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            UUID actorId,
            String reason,
            String requestId,
            Instant createdAt
    ) {
        this.transitionId = transitionId;
        this.taskId = taskId;
        this.companyId = companyId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actorId;
        this.reason = reason;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }
}
