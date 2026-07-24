package com.fowoco.server.approval.application;

import com.fowoco.server.approval.application.error.ApprovalErrorCode;
import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.application.port.EvidenceRepository;
import com.fowoco.server.approval.application.port.ExternalSubmissionRepository;
import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.approval.domain.Evidence;
import com.fowoco.server.approval.domain.ExternalSubmission;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.TaskReadinessChecker;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.application.port.TaskTransitionRecorder;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService implements ApprovalControlPort {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final ActorAuthorizer actorAuthorizer;
    private final TaskRepository taskRepository;
    private final TaskTransitionRecorder transitionRecorder;
    private final TaskReadinessChecker taskReadinessChecker;
    private final ApprovalRequestRepository approvalRepository;
    private final ExternalSubmissionRepository externalSubmissionRepository;
    private final EvidenceRepository evidenceRepository;
    private final AuditEventRepository auditRepository;
    private final SafeJsonService safeJsonService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public ApprovalService(
            ActorAuthorizer actorAuthorizer,
            TaskRepository taskRepository,
            TaskTransitionRecorder transitionRecorder,
            TaskReadinessChecker taskReadinessChecker,
            ApprovalRequestRepository approvalRepository,
            ExternalSubmissionRepository externalSubmissionRepository,
            EvidenceRepository evidenceRepository,
            AuditEventRepository auditRepository,
            SafeJsonService safeJsonService,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.taskRepository = taskRepository;
        this.transitionRecorder = transitionRecorder;
        this.taskReadinessChecker = taskReadinessChecker;
        this.approvalRepository = approvalRepository;
        this.externalSubmissionRepository = externalSubmissionRepository;
        this.evidenceRepository = evidenceRepository;
        this.auditRepository = auditRepository;
        this.safeJsonService = safeJsonService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public ApprovalResult requestApproval(
            UUID taskId,
            RequestApprovalCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        approvalRepository.findPendingByTaskIdAndCompanyId(taskId, actor.companyId())
                .ifPresent(ignored -> {
                    throw new ApiException(ApprovalErrorCode.PENDING_APPROVAL_EXISTS);
                });

        Task task = requireTask(taskId, actor.companyId());
        Instant now = Instant.now(clock);
        TaskStatus previous = task.requestReview(
                taskReadinessChecker.isReadyForReview(task),
                command.expectedVersion(),
                actor.actorId(),
                now
        );
        Task savedTask = taskRepository.save(task);
        recordTransition(savedTask, previous, actor.actorId(), "승인 검토 요청", metadata, now);

        String aiSnapshot = safeJsonService.write(command.aiSnapshot(), false);
        String hrSnapshot = safeJsonService.write(command.hrSnapshot(), true);
        String changedFields = safeJsonService.write(
                command.changedFields() == null ? List.of() : command.changedFields(),
                true
        );
        String sourceVersions = safeJsonService.write(command.sourceVersions(), true);
        ApprovalRequest approval = ApprovalRequest.create(
                uuidGenerator.generate(),
                taskId,
                actor.companyId(),
                savedTask.version(),
                savedTask.contentRevision(),
                savedTask.criticalFingerprint(),
                aiSnapshot,
                hrSnapshot,
                changedFields,
                sourceVersions,
                actor.actorId(),
                now
        );
        ApprovalRequest savedApproval = approvalRepository.save(approval);
        appendAudit(
                savedTask,
                actor,
                AuditAction.APPROVAL_REQUESTED,
                "현재 Task version에 대한 승인 검토를 요청함",
                metadata,
                now
        );
        return result(savedApproval, savedTask);
    }

    @Transactional
    public ApprovalResult approve(
            UUID taskId,
            DecideApprovalCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        requireTaskVersion(task, command.expectedVersion());
        ApprovalRequest approval = requirePendingApproval(taskId, actor.companyId());
        Instant now = Instant.now(clock);
        long currentVersion = task.version();
        TaskStatus previous = task.approve(command.expectedVersion(), actor.actorId(), now);
        Task savedTask = taskRepository.save(task);
        approval.approve(
                currentVersion,
                task.contentRevision(),
                savedTask.criticalFingerprint(),
                savedTask.version(),
                actor.actorId(),
                command.reason(),
                now
        );
        ApprovalRequest savedApproval = approvalRepository.save(approval);
        recordTransition(savedTask, previous, actor.actorId(), command.reason(), metadata, now);
        appendAudit(
                savedTask,
                actor,
                AuditAction.TASK_APPROVED,
                "HR이 현재 업무 내용을 승인함",
                metadata,
                now
        );
        return result(savedApproval, savedTask);
    }

    @Transactional
    public ApprovalResult reject(
            UUID taskId,
            DecideApprovalCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        requireTaskVersion(task, command.expectedVersion());
        ApprovalRequest approval = requirePendingApproval(taskId, actor.companyId());
        Instant now = Instant.now(clock);
        long currentVersion = task.version();
        TaskStatus previous = task.reject(command.expectedVersion(), actor.actorId(), now);
        Task savedTask = taskRepository.save(task);
        approval.reject(currentVersion, actor.actorId(), command.reason(), now);
        ApprovalRequest savedApproval = approvalRepository.save(approval);
        recordTransition(savedTask, previous, actor.actorId(), command.reason(), metadata, now);
        appendAudit(
                savedTask,
                actor,
                AuditAction.TASK_REJECTED,
                "HR이 승인 요청을 반려함",
                metadata,
                now
        );
        return result(savedApproval, savedTask);
    }

    @Transactional
    public TaskActionResult recordExternalSubmission(
            UUID taskId,
            RecordExternalSubmissionCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        requireValidApproval(task);
        Instant now = Instant.now(clock);
        Instant submittedAt = command.submittedAt() == null ? now : command.submittedAt();
        if (submittedAt.isAfter(now)) {
            throw new ApiException(ApprovalErrorCode.INVALID_EXTERNAL_SUBMISSION);
        }
        String destination = safeJsonService.safeText(command.destination(), 160, true);
        String safeReference = safeJsonService.safeText(command.safeReference(), 300, true);
        TaskStatus previous = task.recordExternalSubmission(
                command.expectedVersion(),
                actor.actorId(),
                now
        );
        Task savedTask = taskRepository.save(task);
        ExternalSubmission submission = externalSubmissionRepository.save(new ExternalSubmission(
                uuidGenerator.generate(),
                taskId,
                actor.companyId(),
                destination,
                safeReference,
                actor.actorId(),
                submittedAt,
                now
        ));
        recordTransition(savedTask, previous, actor.actorId(), "외부기관 제출 기록", metadata, now);
        appendAudit(
                savedTask,
                actor,
                AuditAction.EXTERNAL_SUBMISSION_RECORDED,
                "외부기관 제출과 안전한 접수 참조값을 기록함",
                metadata,
                now
        );
        return new TaskActionResult(
                submission.externalSubmissionId(),
                taskId,
                savedTask.status(),
                savedTask.version()
        );
    }

    @Transactional
    public TaskActionResult recordEvidence(
            UUID taskId,
            RecordEvidenceCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        if (task.status() != TaskStatus.APPROVED
                && task.status() != TaskStatus.WAITING_WORKER
                && task.status() != TaskStatus.WAITING_EXTERNAL) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        Instant now = Instant.now(clock);
        Instant recordedAt = command.recordedAt() == null ? now : command.recordedAt();
        if (recordedAt.isAfter(now) || command.evidenceType() == null) {
            throw new ApiException(ApprovalErrorCode.INVALID_EVIDENCE);
        }
        String fileReference = safeJsonService.safeText(command.fileReference(), 300, false);
        String note = safeJsonService.safeText(command.note(), 500, false);
        if (fileReference == null && note == null) {
            throw new ApiException(ApprovalErrorCode.INVALID_EVIDENCE);
        }
        Evidence evidence = evidenceRepository.save(new Evidence(
                uuidGenerator.generate(),
                taskId,
                actor.companyId(),
                command.evidenceType(),
                fileReference,
                note,
                actor.actorId(),
                recordedAt,
                now
        ));
        appendAudit(
                task,
                actor,
                AuditAction.EVIDENCE_RECORDED,
                "업무 완료 검증용 증빙을 연결함",
                metadata,
                now
        );
        return new TaskActionResult(evidence.evidenceId(), taskId, task.status(), task.version());
    }

    @Transactional
    public TaskActionResult complete(
            UUID taskId,
            CompleteTaskCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        boolean approved = hasValidApproval(
                taskId,
                actor.companyId(),
                task.contentRevision(),
                task.criticalFingerprint()
        );
        boolean evidencePresent = evidenceRepository.existsByTaskIdAndCompanyId(taskId, actor.companyId());
        Instant now = Instant.now(clock);
        TaskStatus previous = task.complete(
                approved,
                evidencePresent,
                command.expectedVersion(),
                actor.actorId(),
                now
        );
        Task savedTask = taskRepository.save(task);
        recordTransition(savedTask, previous, actor.actorId(), "업무 완료", metadata, now);
        appendAudit(
                savedTask,
                actor,
                AuditAction.TASK_COMPLETED,
                "승인·외부 제출·증빙 확인 후 업무를 완료함",
                metadata,
                now
        );
        return new TaskActionResult(savedTask.taskId(), taskId, savedTask.status(), savedTask.version());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasValidApproval(
            UUID taskId,
            UUID companyId,
            long contentRevision,
            String criticalFingerprint
    ) {
        return approvalRepository.findLatestApprovedByTaskIdAndCompanyId(taskId, companyId)
                .filter(approval -> approval.isValidFor(contentRevision, criticalFingerprint))
                .isPresent();
    }

    @Override
    @Transactional
    public void invalidateForCriticalChange(
            UUID taskId,
            ActorContext actor,
            String reason,
            Instant occurredAt,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        List<ApprovalRequest> active = approvalRepository.findActiveByTaskIdAndCompanyId(
                taskId,
                actor.companyId()
        );
        active.forEach(approval -> {
            approval.invalidate(reason, occurredAt);
            approvalRepository.save(approval);
        });
        if (!active.isEmpty()) {
            appendAudit(
                    task,
                    actor,
                    AuditAction.APPROVAL_INVALIDATED,
                    "중요 업무값 변경으로 기존 승인을 무효화함",
                    metadata,
                    occurredAt
            );
        }
    }

    private Task requireTask(UUID taskId, UUID companyId) {
        return taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
    }

    private ApprovalRequest requirePendingApproval(UUID taskId, UUID companyId) {
        return approvalRepository.findPendingByTaskIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ApiException(ApprovalErrorCode.APPROVAL_REQUEST_NOT_FOUND));
    }

    private void requireTaskVersion(Task task, long expectedVersion) {
        if (task.version() != expectedVersion) {
            throw new ApiException(TaskErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private ApprovalRequest requireValidApproval(Task task) {
        return approvalRepository.findLatestApprovedByTaskIdAndCompanyId(
                task.taskId(),
                task.companyId()
                )
                .filter(approval -> approval.isValidFor(
                        task.contentRevision(),
                        task.criticalFingerprint()
                ))
                .orElseThrow(() -> new ApiException(ApprovalErrorCode.APPROVAL_INVALIDATED));
    }

    private void recordTransition(
            Task task,
            TaskStatus previous,
            UUID actorId,
            String reason,
            RequestMetadata metadata,
            Instant now
    ) {
        transitionRecorder.record(
                uuidGenerator.generate(),
                task.taskId(),
                task.companyId(),
                previous,
                task.status(),
                actorId,
                normalizeReason(reason),
                metadata.requestId(),
                now
        );
    }

    private void appendAudit(
            Task task,
            ActorContext actor,
            AuditAction action,
            String summary,
            RequestMetadata metadata,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                task.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                action,
                AuditTargetType.TASK,
                task.taskId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                summary,
                now
        ));
    }

    private UserRole effectiveRole(ActorContext actor) {
        return actor.roles().stream()
                .min(Comparator.comparingInt(this::rolePriority))
                .orElseThrow();
    }

    private int rolePriority(UserRole role) {
        return switch (role) {
            case ADMIN -> 0;
            case HR -> 1;
            case VIEWER -> 2;
        };
    }

    private ApprovalResult result(ApprovalRequest approval, Task task) {
        return new ApprovalResult(
                approval.approvalRequestId(),
                task.taskId(),
                approval.status(),
                task.status(),
                task.contentRevision(),
                task.version(),
                approval.requestedAt(),
                approval.decidedAt()
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return safeJsonService.safeText(reason, 500, false);
    }
}
