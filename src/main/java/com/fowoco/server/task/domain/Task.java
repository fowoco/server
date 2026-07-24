package com.fowoco.server.task.domain;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class Task {

    private final UUID taskId;
    private final UUID companyId;
    private final UUID workerId;
    private final UUID caseId;
    private final TaskType taskType;
    private final String workflowId;
    private final String workflowCatalogVersion;
    private String title;
    private String description;
    private String businessDataJson;
    private String criticalFingerprint;
    private long contentRevision;
    private final TaskSource source;
    private TaskStatus status;
    private LocalDate dueDate;
    private final UUID createdBy;
    private UUID updatedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Task(
            UUID taskId,
            UUID companyId,
            UUID workerId,
            UUID caseId,
            TaskType taskType,
            String workflowId,
            String workflowCatalogVersion,
            String title,
            String description,
            String businessDataJson,
            String criticalFingerprint,
            long contentRevision,
            TaskSource source,
            TaskStatus status,
            LocalDate dueDate,
            UUID createdBy,
            UUID updatedBy,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.taskId = Objects.requireNonNull(taskId);
        this.companyId = Objects.requireNonNull(companyId);
        this.workerId = Objects.requireNonNull(workerId);
        this.caseId = Objects.requireNonNull(caseId);
        this.taskType = Objects.requireNonNull(taskType);
        this.workflowId = requireText(workflowId, "workflowId");
        this.workflowCatalogVersion = requireText(workflowCatalogVersion, "workflowCatalogVersion");
        this.title = requireText(title, "title");
        this.description = normalizeNullable(description);
        this.businessDataJson = requireText(businessDataJson, "businessDataJson");
        this.criticalFingerprint = requireFingerprint(criticalFingerprint);
        if (contentRevision < 0) {
            throw new IllegalArgumentException("contentRevision must not be negative");
        }
        this.contentRevision = contentRevision;
        this.source = Objects.requireNonNull(source);
        this.status = Objects.requireNonNull(status);
        this.dueDate = dueDate;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.updatedBy = Objects.requireNonNull(updatedBy);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static Task create(
            UUID taskId,
            UUID companyId,
            UUID workerId,
            UUID caseId,
            TaskType taskType,
            String workflowId,
            String workflowCatalogVersion,
            String title,
            String description,
            String businessDataJson,
            String criticalFingerprint,
            TaskSource source,
            TaskStatus initialStatus,
            LocalDate dueDate,
            UUID actorId,
            Instant now
    ) {
        if (initialStatus != TaskStatus.DRAFT && initialStatus != TaskStatus.NEEDS_INFO) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
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
                0,
                source,
                initialStatus,
                dueDate,
                actorId,
                actorId,
                now,
                now,
                0
        );
    }

    public TaskStatus requestReview(boolean requirementsSatisfied, long expectedVersion, UUID actorId, Instant now) {
        requireVersion(expectedVersion);
        if (status != TaskStatus.DRAFT && status != TaskStatus.NEEDS_INFO) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        if (!requirementsSatisfied) {
            throw new ApiException(TaskErrorCode.TASK_REQUIREMENTS_MISSING);
        }
        return transition(TaskStatus.READY_FOR_REVIEW, actorId, now);
    }

    public TaskStatus approve(long expectedVersion, UUID actorId, Instant now) {
        requireVersion(expectedVersion);
        requireStatus(TaskStatus.READY_FOR_REVIEW);
        return transition(TaskStatus.APPROVED, actorId, now);
    }

    public TaskStatus reject(long expectedVersion, UUID actorId, Instant now) {
        requireVersion(expectedVersion);
        requireStatus(TaskStatus.READY_FOR_REVIEW);
        return transition(TaskStatus.DRAFT, actorId, now);
    }

    public TaskStatus recordExternalSubmission(long expectedVersion, UUID actorId, Instant now) {
        requireVersion(expectedVersion);
        if (status != TaskStatus.APPROVED && status != TaskStatus.WAITING_WORKER) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        return transition(TaskStatus.WAITING_EXTERNAL, actorId, now);
    }

    public TaskStatus complete(
            boolean currentVersionApproved,
            boolean requiredEvidencePresent,
            long expectedVersion,
            UUID actorId,
            Instant now
    ) {
        requireVersion(expectedVersion);
        if (status != TaskStatus.APPROVED
                && status != TaskStatus.WAITING_WORKER
                && status != TaskStatus.WAITING_EXTERNAL) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        if (!currentVersionApproved) {
            throw new ApiException(TaskErrorCode.APPROVAL_REQUIRED);
        }
        if (!requiredEvidencePresent) {
            throw new ApiException(TaskErrorCode.EVIDENCE_REQUIRED);
        }
        return transition(TaskStatus.COMPLETED, actorId, now);
    }

    public TaskStatus cancel(long expectedVersion, UUID actorId, Instant now) {
        requireVersion(expectedVersion);
        if (status.isTerminal()) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        return transition(TaskStatus.CANCELLED, actorId, now);
    }

    public UpdateOutcome updateContent(
            String title,
            String description,
            String businessDataJson,
            String criticalFingerprint,
            LocalDate dueDate,
            boolean requirementsSatisfied,
            long expectedVersion,
            UUID actorId,
            Instant now
    ) {
        requireVersion(expectedVersion);
        if (status.isTerminal()) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        String nextFingerprint = requireFingerprint(criticalFingerprint);
        boolean criticalChanged = !this.criticalFingerprint.equals(nextFingerprint);
        boolean approvalInvalidated = criticalChanged
                && (status == TaskStatus.READY_FOR_REVIEW
                || status == TaskStatus.APPROVED
                || status == TaskStatus.WAITING_WORKER
                || status == TaskStatus.WAITING_EXTERNAL);

        this.title = requireText(title, "title");
        this.description = normalizeNullable(description);
        this.businessDataJson = requireText(businessDataJson, "businessDataJson");
        this.criticalFingerprint = nextFingerprint;
        if (criticalChanged) {
            this.contentRevision++;
        }
        this.dueDate = dueDate;
        this.updatedBy = Objects.requireNonNull(actorId);
        this.updatedAt = Objects.requireNonNull(now);
        if (criticalChanged && (approvalInvalidated
                || status == TaskStatus.DRAFT
                || status == TaskStatus.NEEDS_INFO)) {
            this.status = requirementsSatisfied ? TaskStatus.DRAFT : TaskStatus.NEEDS_INFO;
        }
        return new UpdateOutcome(criticalChanged, approvalInvalidated);
    }

    /**
     * Re-evaluates required slots and checklist items after a checklist command.
     *
     * <p>A completed checklist item never approves a task automatically. If all requirements become
     * complete the task only returns to {@link TaskStatus#DRAFT}. If a required item becomes
     * incomplete after review or approval, the approved content revision is invalidated.</p>
     */
    public RequirementsOutcome reassessRequirements(
            boolean requirementsSatisfied,
            long expectedVersion,
            UUID actorId,
            Instant now
    ) {
        requireVersion(expectedVersion);
        if (status.isTerminal()) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        TaskStatus previous = status;
        boolean approvalInvalidated = !requirementsSatisfied
                && (status == TaskStatus.READY_FOR_REVIEW
                || status == TaskStatus.APPROVED
                || status == TaskStatus.WAITING_WORKER
                || status == TaskStatus.WAITING_EXTERNAL);
        if (approvalInvalidated) {
            contentRevision++;
            status = TaskStatus.NEEDS_INFO;
        } else if (status == TaskStatus.NEEDS_INFO && requirementsSatisfied) {
            status = TaskStatus.DRAFT;
        }
        if (status != previous) {
            updatedBy = Objects.requireNonNull(actorId);
            updatedAt = Objects.requireNonNull(now);
        }
        return new RequirementsOutcome(previous, approvalInvalidated);
    }

    private TaskStatus transition(TaskStatus next, UUID actorId, Instant now) {
        TaskStatus previous = status;
        status = next;
        updatedBy = Objects.requireNonNull(actorId);
        updatedAt = Objects.requireNonNull(now);
        return previous;
    }

    private void requireStatus(TaskStatus expected) {
        if (status != expected) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
    }

    private void requireVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new ApiException(TaskErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireFingerprint(String value) {
        String fingerprint = requireText(value, "criticalFingerprint");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("criticalFingerprint must be a lowercase SHA-256 hex value");
        }
        return fingerprint;
    }

    public UUID taskId() {
        return taskId;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID workerId() {
        return workerId;
    }

    public UUID caseId() {
        return caseId;
    }

    public TaskType taskType() {
        return taskType;
    }

    public String workflowId() {
        return workflowId;
    }

    public String workflowCatalogVersion() {
        return workflowCatalogVersion;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String businessDataJson() {
        return businessDataJson;
    }

    public String criticalFingerprint() {
        return criticalFingerprint;
    }

    public long contentRevision() {
        return contentRevision;
    }

    public TaskSource source() {
        return source;
    }

    public TaskStatus status() {
        return status;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public UUID updatedBy() {
        return updatedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public record UpdateOutcome(boolean criticalChanged, boolean approvalInvalidated) {
    }

    public record RequirementsOutcome(TaskStatus previousStatus, boolean approvalInvalidated) {
    }
}
