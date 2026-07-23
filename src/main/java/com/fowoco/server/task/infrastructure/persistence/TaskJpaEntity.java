package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "task",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_task_id_company",
                columnNames = {"task_id", "company_id"}
        )
)
public class TaskJpaEntity {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "worker_id", nullable = false, updatable = false)
    private UUID workerId;
    @Column(name = "case_id", nullable = false, updatable = false)
    private UUID caseId;
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 40, updatable = false)
    private TaskType taskType;
    @Column(name = "workflow_id", nullable = false, length = 100, updatable = false)
    private String workflowId;
    @Column(name = "workflow_catalog_version", nullable = false, length = 80, updatable = false)
    private String workflowCatalogVersion;
    @Column(name = "title", nullable = false, length = 160)
    private String title;
    @Column(name = "description", length = 2000)
    private String description;
    @Column(name = "business_data_json", nullable = false, columnDefinition = "TEXT")
    private String businessDataJson;
    @Column(name = "critical_fingerprint", nullable = false, length = 64)
    private String criticalFingerprint;
    @Column(name = "content_revision", nullable = false)
    private long contentRevision;
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30, updatable = false)
    private TaskSource source;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TaskStatus status;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TaskJpaEntity() {
    }

    public TaskJpaEntity(Task task) {
        copyFrom(task);
        this.version = task.version();
    }

    public void apply(Task task) {
        this.title = task.title();
        this.description = task.description();
        this.businessDataJson = task.businessDataJson();
        this.criticalFingerprint = task.criticalFingerprint();
        this.contentRevision = task.contentRevision();
        this.status = task.status();
        this.dueDate = task.dueDate();
        this.updatedBy = task.updatedBy();
        this.updatedAt = task.updatedAt();
    }

    private void copyFrom(Task task) {
        this.taskId = task.taskId();
        this.companyId = task.companyId();
        this.workerId = task.workerId();
        this.caseId = task.caseId();
        this.taskType = task.taskType();
        this.workflowId = task.workflowId();
        this.workflowCatalogVersion = task.workflowCatalogVersion();
        this.title = task.title();
        this.description = task.description();
        this.businessDataJson = task.businessDataJson();
        this.criticalFingerprint = task.criticalFingerprint();
        this.contentRevision = task.contentRevision();
        this.source = task.source();
        this.status = task.status();
        this.dueDate = task.dueDate();
        this.createdBy = task.createdBy();
        this.updatedBy = task.updatedBy();
        this.createdAt = task.createdAt();
        this.updatedAt = task.updatedAt();
    }

    public Task toDomain() {
        return new Task(
                taskId,
                companyId,
                workerId,
                caseId,
                taskType,
                workflowId,
                workflowCatalogVersion,
                title,
                description,
                businessDataJson,
                criticalFingerprint,
                contentRevision,
                source,
                status,
                dueDate,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                version
        );
    }
}
