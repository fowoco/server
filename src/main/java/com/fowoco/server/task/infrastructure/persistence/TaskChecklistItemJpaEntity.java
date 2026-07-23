package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.domain.TaskChecklistItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "task_checklist_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_task_checklist_code",
                columnNames = {"task_id", "item_code"}
        )
)
class TaskChecklistItemJpaEntity {

    @Id
    @Column(name = "checklist_item_id", nullable = false, updatable = false)
    private UUID checklistItemId;
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "item_code", nullable = false, length = 100, updatable = false)
    private String itemCode;
    @Column(name = "label", nullable = false, length = 300, updatable = false)
    private String label;
    @Column(name = "required", nullable = false, updatable = false)
    private boolean required;
    @Column(name = "completed", nullable = false)
    private boolean completed;
    @Column(name = "completed_by")
    private UUID completedBy;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TaskChecklistItemJpaEntity() {
    }

    TaskChecklistItemJpaEntity(TaskChecklistItem item) {
        this.checklistItemId = item.checklistItemId();
        this.taskId = item.taskId();
        this.companyId = item.companyId();
        this.itemCode = item.itemCode();
        this.label = item.label();
        this.required = item.required();
        this.createdAt = item.createdAt();
        apply(item);
        this.version = item.version();
    }

    void apply(TaskChecklistItem item) {
        this.completed = item.completed();
        this.completedBy = item.completedBy();
        this.completedAt = item.completedAt();
        this.updatedAt = item.updatedAt();
    }

    TaskChecklistItem toDomain() {
        return new TaskChecklistItem(
                checklistItemId,
                taskId,
                companyId,
                itemCode,
                label,
                required,
                completed,
                completedBy,
                completedAt,
                createdAt,
                updatedAt,
                version
        );
    }
}
