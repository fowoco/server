package com.fowoco.server.task.application;

import com.fowoco.server.approval.application.ApprovalControlPort;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.CompanyMemberAccount;
import com.fowoco.server.auth.application.port.CompanyMemberDirectory;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.reliability.application.port.DomainEventPublisher;
import com.fowoco.server.task.application.TaskContentCodec.EncodedTaskContent;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.application.port.TaskCaseRegistrar;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.application.port.TaskRepository.TaskPage;
import com.fowoco.server.task.application.port.TaskRepository.TaskSearchCriteria;
import com.fowoco.server.task.application.port.TaskTransitionRecorder;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskChecklistItem;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskTargetType;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.application.WorkerTaskContext;
import com.fowoco.server.worker.application.port.WorkerTaskContextReader;
import com.fowoco.server.workflow.application.WorkflowCatalogService;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskWorkflowService {

    private static final String AUDIT_EVENT_VERSION = "1";
    private final ActorAuthorizer actorAuthorizer;
    private final CompanyMemberDirectory companyMemberDirectory;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final TaskRepository taskRepository;
    private final TaskChecklistRepository checklistRepository;
    private final TaskCaseRegistrar taskCaseRegistrar;
    private final TaskTransitionRecorder transitionRecorder;
    private final WorkerTaskContextReader workerReader;
    private final WorkflowCatalogService catalogService;
    private final ApprovalControlPort approvalControl;
    private final AuditEventRepository auditRepository;
    private final DomainEventPublisher eventPublisher;
    private final TaskContentCodec contentCodec;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public TaskWorkflowService(
            ActorAuthorizer actorAuthorizer,
            CompanyMemberDirectory companyMemberDirectory,
            TenantDatabaseContext tenantDatabaseContext,
            TaskRepository taskRepository,
            TaskChecklistRepository checklistRepository,
            TaskCaseRegistrar taskCaseRegistrar,
            TaskTransitionRecorder transitionRecorder,
            WorkerTaskContextReader workerReader,
            WorkflowCatalogService catalogService,
            ApprovalControlPort approvalControl,
            AuditEventRepository auditRepository,
            DomainEventPublisher eventPublisher,
            TaskContentCodec contentCodec,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.companyMemberDirectory = companyMemberDirectory;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.taskRepository = taskRepository;
        this.checklistRepository = checklistRepository;
        this.taskCaseRegistrar = taskCaseRegistrar;
        this.transitionRecorder = transitionRecorder;
        this.workerReader = workerReader;
        this.catalogService = catalogService;
        this.approvalControl = approvalControl;
        this.auditRepository = auditRepository;
        this.eventPublisher = eventPublisher;
        this.contentCodec = contentCodec;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public TaskResult create(
            CreateTaskCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        actorAuthorizer.requireHrWrite(actor);
        WorkflowDefinition workflow = catalogService.requireWorkflow(command.workflowId());
        if (!workflow.supportedTaskTypes().contains(command.taskType())) {
            throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        }
        WorkerTaskContext worker = resolveCreateTarget(command, workflow, actor.companyId());
        Map<String, Object> businessData =
                command.businessData() == null ? Map.of() : command.businessData();
        List<String> missingSlots = missingRequiredSlots(
                workflow,
                worker,
                command.dueDate(),
                businessData
        );
        EncodedTaskContent content = contentCodec.encode(
                command.targetType(),
                command.workerId(),
                workflow.workflowId(),
                command.taskType().name(),
                command.title(),
                command.description(),
                command.dueDate(),
                businessData
        );
        Instant now = Instant.now(clock);
        UUID taskId = uuidGenerator.generate();
        UUID caseId = command.targetType() == TaskTargetType.WORKER
                ? (command.caseId() == null ? uuidGenerator.generate() : command.caseId())
                : null;
        Task task = Task.create(
                taskId,
                actor.companyId(),
                command.targetType(),
                command.workerId(),
                caseId,
                command.taskType(),
                workflow.workflowId(),
                catalogService.getActiveCatalog().bundleVersion(),
                command.title(),
                command.description(),
                content.businessDataJson(),
                content.criticalFingerprint(),
                TaskSource.MANUAL,
                missingSlots.isEmpty() ? TaskStatus.DRAFT : TaskStatus.NEEDS_INFO,
                command.dueDate(),
                actor.actorId(),
                now
        );
        if (task.targetType() == TaskTargetType.WORKER) {
            taskCaseRegistrar.register(task, workflow, LocalDate.now(clock));
        }
        Task savedTask = taskRepository.save(task);
        List<TaskChecklistItem> checklistItems = checklistRepository.saveAll(
                workflow.checklistItems().stream()
                        .map(template -> TaskChecklistItem.create(
                                uuidGenerator.generate(),
                                savedTask.taskId(),
                                actor.companyId(),
                                template.itemCode(),
                                template.label(),
                                template.required(),
                                now
                        ))
                        .toList()
        );
        appendAudit(
                savedTask,
                actor,
                AuditAction.TASK_CREATED,
                "수동 업무카드를 생성함",
                metadata,
                now
        );
        eventPublisher.publish(TaskDomainEvents.taskCreated(
                uuidGenerator.generate(),
                savedTask,
                actor,
                metadata,
                now
        ));
        return toResult(savedTask, checklistItems, worker, workflow);
    }

    @Transactional(readOnly = true)
    public TaskPageResult findAll(
            TaskStatus status,
            TaskType taskType,
            TaskTargetType targetType,
            TaskSource source,
            UUID workerId,
            UUID caseId,
            LocalDate dueFrom,
            LocalDate dueTo,
            String keyword,
            int page,
            int size,
            ActorContext actor
    ) {
        bindTenant(actor);
        requireRead(actor);
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new ApiException(TaskErrorCode.INVALID_TASK_FILTER);
        }
        TaskPage result = taskRepository.findAll(new TaskSearchCriteria(
                actor.companyId(),
                status,
                taskType,
                targetType,
                source,
                workerId,
                caseId,
                dueFrom,
                dueTo,
                keyword,
                page,
                size
        ));
        return new TaskPageResult(
                taskSummaryViews(result.items(), actor.companyId()),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    @Transactional(readOnly = true)
    public TaskResult findById(UUID taskId, ActorContext actor) {
        bindTenant(actor);
        requireRead(actor);
        Task task = requireTask(taskId, actor.companyId());
        return toResult(
                task,
                checklistRepository.findAllByTaskIdAndCompanyId(taskId, actor.companyId()),
                findWorker(task, actor.companyId()),
                catalogService.requireWorkflow(task.workflowId())
        );
    }

    @Transactional
    public TaskResult update(
            UUID taskId,
            UpdateTaskCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        WorkflowDefinition workflow = catalogService.requireWorkflow(task.workflowId());
        WorkerTaskContext worker = findWorker(task, actor.companyId());
        Map<String, Object> businessData =
                command.businessData() == null ? Map.of() : command.businessData();
        List<String> missingSlots = missingRequiredSlots(
                workflow,
                worker,
                command.dueDate(),
                businessData
        );
        List<TaskChecklistItem> checklistItems =
                checklistRepository.findAllByTaskIdAndCompanyId(taskId, actor.companyId());
        boolean checklistSatisfied = checklistItems.stream()
                .noneMatch(item -> item.required() && !item.completed());
        EncodedTaskContent content = contentCodec.encode(
                task.targetType(),
                task.workerId(),
                task.workflowId(),
                task.taskType().name(),
                command.title(),
                command.description(),
                command.dueDate(),
                businessData
        );
        Instant now = Instant.now(clock);
        TaskStatus previous = task.status();
        Task.UpdateOutcome outcome = task.updateContent(
                command.title(),
                command.description(),
                content.businessDataJson(),
                content.criticalFingerprint(),
                command.dueDate(),
                missingSlots.isEmpty() && checklistSatisfied,
                command.expectedVersion(),
                actor.actorId(),
                now
        );
        Task savedTask = taskRepository.save(task);
        recordTransitionIfChanged(
                savedTask,
                previous,
                actor.actorId(),
                "업무 내용 수정",
                metadata,
                now
        );
        if (outcome.approvalInvalidated()) {
            if (savedTask.status() == TaskStatus.DRAFT) {
                savedTask = approvalControl.replaceReviewAfterCriticalChange(
                        taskId,
                        actor,
                        "업무 핵심값이 변경됨",
                        now,
                        metadata
                );
            } else {
                approvalControl.invalidateForCriticalChange(
                        taskId,
                        actor,
                        "업무 핵심값이 변경됨",
                        now,
                        metadata
                );
            }
        }
        appendAudit(
                savedTask,
                actor,
                AuditAction.TASK_UPDATED,
                outcome.criticalChanged()
                        ? "승인 대상 업무 내용을 수정함"
                        : "업무 내용을 다시 저장함",
                metadata,
                now
        );
        return toResult(
                savedTask,
                checklistItems,
                worker,
                workflow
        );
    }

    @Transactional
    public TaskResult changeAssignee(
            UUID taskId,
            ChangeTaskAssigneeCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        TaskAssigneeView nextAssignee = requireAssignableAssignee(
                actor.companyId(),
                command.assigneeId()
        );
        UUID previousAssigneeId = task.assigneeId();
        Instant now = Instant.now(clock);
        boolean changed = task.changeAssignee(
                command.assigneeId(),
                command.expectedVersion(),
                actor.actorId(),
                now
        );
        Task savedTask = changed ? taskRepository.save(task) : task;
        if (changed) {
            appendAudit(
                    savedTask,
                    actor,
                    AuditAction.TASK_ASSIGNEE_CHANGED,
                    "업무 담당자를 변경함 (%s → %s)".formatted(
                            previousAssigneeId,
                            savedTask.assigneeId()
                    ),
                    metadata,
                    now
            );
        }
        return toResult(
                savedTask,
                checklistRepository.findAllByTaskIdAndCompanyId(taskId, actor.companyId()),
                findWorker(savedTask, actor.companyId()),
                catalogService.requireWorkflow(savedTask.workflowId()),
                nextAssignee
        );
    }

    @Transactional
    public TaskResult updateChecklistItem(
            UUID taskId,
            UUID checklistItemId,
            UpdateChecklistItemCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        TaskChecklistItem item = checklistRepository
                .findByIdAndTaskIdAndCompanyId(checklistItemId, taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.CHECKLIST_ITEM_NOT_FOUND));
        Instant now = Instant.now(clock);
        boolean changed = item.updateCompletion(
                command.completed(),
                command.expectedVersion(),
                actor.actorId(),
                now
        );
        if (changed) {
            checklistRepository.save(item);
        }
        List<TaskChecklistItem> items =
                checklistRepository.findAllByTaskIdAndCompanyId(taskId, actor.companyId());
        WorkerTaskContext worker = findWorker(task, actor.companyId());
        WorkflowDefinition workflow = catalogService.requireWorkflow(task.workflowId());
        Map<String, Object> businessData = contentCodec.decodeBusinessData(task.businessDataJson());
        boolean slotsSatisfied = missingRequiredSlots(
                workflow,
                worker,
                task.dueDate(),
                businessData
        ).isEmpty();
        boolean checklistSatisfied = items.stream()
                .noneMatch(candidate -> candidate.required() && !candidate.completed());
        TaskStatus previous = task.status();
        Task.RequirementsOutcome requirements = task.reassessRequirements(
                slotsSatisfied && checklistSatisfied,
                command.expectedTaskVersion(),
                actor.actorId(),
                now
        );
        Task savedTask = previous == task.status() ? task : taskRepository.save(task);
        if (requirements.approvalInvalidated()) {
            approvalControl.invalidateForCriticalChange(
                    taskId,
                    actor,
                    "필수 체크리스트 항목이 미완료로 변경됨",
                    now,
                    metadata
            );
            eventPublisher.publish(TaskDomainEvents.taskNeedsInfo(
                    uuidGenerator.generate(), savedTask, actor, metadata, now
            ));
        }
        recordTransitionIfChanged(
                savedTask,
                previous,
                actor.actorId(),
                "필수 체크리스트 재평가",
                metadata,
                now
        );
        if (changed) {
            appendAudit(
                    savedTask,
                    actor,
                    AuditAction.CHECKLIST_ITEM_UPDATED,
                    command.completed()
                            ? "체크리스트 항목을 완료 처리함"
                            : "체크리스트 항목을 미완료 처리함",
                    metadata,
                    now
            );
        }
        return toResult(savedTask, items, worker, workflow);
    }

    @Transactional
    public TaskResult cancel(
            UUID taskId,
            CancelTaskCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        actorAuthorizer.requireHrWrite(actor);
        Task task = requireTask(taskId, actor.companyId());
        String reason = contentCodec.safeText(command.reason(), 500);
        Instant now = Instant.now(clock);
        TaskStatus previous = task.cancel(
                command.expectedVersion(),
                actor.actorId(),
                now
        );
        Task savedTask = taskRepository.save(task);
        approvalControl.invalidateForCriticalChange(taskId, actor, reason, now, metadata);
        recordTransitionIfChanged(
                savedTask,
                previous,
                actor.actorId(),
                reason,
                metadata,
                now
        );
        appendAudit(
                savedTask,
                actor,
                AuditAction.TASK_CANCELLED,
                "사유를 기록하고 업무카드를 취소함",
                metadata,
                now
        );
        eventPublisher.publish(TaskDomainEvents.taskCancelled(
                uuidGenerator.generate(),
                savedTask,
                previous,
                actor,
                metadata,
                now
        ));
        return toResult(
                savedTask,
                checklistRepository.findAllByTaskIdAndCompanyId(taskId, actor.companyId()),
                findWorker(task, actor.companyId()),
                catalogService.requireWorkflow(task.workflowId())
        );
    }

    private TaskResult toResult(
            Task task,
            List<TaskChecklistItem> checklistItems,
            WorkerTaskContext worker,
            WorkflowDefinition workflow
    ) {
        return toResult(
                task,
                checklistItems,
                worker,
                workflow,
                requireAssignee(task, task.companyId())
        );
    }

    private TaskResult toResult(
            Task task,
            List<TaskChecklistItem> checklistItems,
            WorkerTaskContext worker,
            WorkflowDefinition workflow,
            TaskAssigneeView assignee
    ) {
        Map<String, Object> businessData = contentCodec.decodeBusinessData(task.businessDataJson());
        return new TaskResult(
                task,
                assignee,
                businessData,
                checklistItems,
                missingRequiredSlots(workflow, worker, task.dueDate(), businessData)
        );
    }

    private List<TaskSummaryView> taskSummaryViews(List<Task> tasks, UUID companyId) {
        Map<UUID, TaskAssigneeView> assignees = new HashMap<>();
        companyMemberDirectory.findByCompanyId(companyId, null, false)
                .forEach(member -> assignees.put(member.userId(), toAssigneeView(member)));
        return tasks.stream()
                .map(task -> new TaskSummaryView(
                        task,
                        requireAssignee(task, assignees)
                ))
                .toList();
    }

    private TaskAssigneeView requireAssignableAssignee(UUID companyId, UUID assigneeId) {
        CompanyMemberAccount member = requireCompanyMember(companyId, assigneeId);
        if (!member.active() || member.role() == UserRole.VIEWER) {
            throw new ApiException(TaskErrorCode.TASK_ASSIGNEE_NOT_ASSIGNABLE);
        }
        return toAssigneeView(member);
    }

    private TaskAssigneeView requireAssignee(Task task, UUID companyId) {
        return toAssigneeView(requireCompanyMember(companyId, task.assigneeId()));
    }

    private TaskAssigneeView requireAssignee(
            Task task,
            Map<UUID, TaskAssigneeView> assignees
    ) {
        TaskAssigneeView assignee = assignees.get(task.assigneeId());
        if (assignee == null) {
            throw new IllegalStateException("Persisted task assignee is missing");
        }
        return assignee;
    }

    private CompanyMemberAccount requireCompanyMember(UUID companyId, UUID userId) {
        return companyMemberDirectory.findByCompanyId(companyId, null, false).stream()
                .filter(member -> member.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_ASSIGNEE_NOT_FOUND));
    }

    private TaskAssigneeView toAssigneeView(CompanyMemberAccount member) {
        return new TaskAssigneeView(member.userId(), member.displayName());
    }

    private List<String> missingRequiredSlots(
            WorkflowDefinition workflow,
            WorkerTaskContext worker,
            LocalDate dueDate,
            Map<String, Object> businessData
    ) {
        List<String> missing = new ArrayList<>();
        workflow.requiredSlots().stream().sorted().forEach(slot -> {
            boolean present = switch (slot) {
                case "worker_id" -> worker != null && worker.workerId() != null;
                case "due_at", "due_date" -> dueDate != null;
                case "contract_start_date" -> worker != null && worker.contractStartDate() != null;
                case "contract_end_date" -> worker != null && worker.contractEndDate() != null;
                case "stay_expiry_date" -> worker != null && worker.stayExpiryDate() != null;
                default -> hasBusinessValue(businessData.get(slot));
            };
            if (!present) {
                missing.add(slot);
            }
        });
        return List.copyOf(missing);
    }

    private boolean hasBusinessValue(Object value) {
        if (value == null) {
            return false;
        }
        return !(value instanceof String text) || !text.isBlank();
    }

    private WorkerTaskContext requireWorker(UUID workerId, UUID companyId) {
        return workerReader.findByIdAndCompanyId(workerId, companyId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.WORKER_NOT_FOUND));
    }

    private WorkerTaskContext findWorker(Task task, UUID companyId) {
        return task.targetType() == TaskTargetType.WORKER
                ? requireWorker(task.workerId(), companyId)
                : null;
    }

    private WorkerTaskContext resolveCreateTarget(
            CreateTaskCommand command,
            WorkflowDefinition workflow,
            UUID companyId
    ) {
        if (command.targetType() == TaskTargetType.COMPANY) {
            if (command.workerId() != null
                    || command.caseId() != null
                    || workflow.requiredSlots().contains("worker_id")) {
                throw new ApiException(TaskErrorCode.INVALID_TASK_TARGET);
            }
            return null;
        }
        if (command.workerId() == null) {
            throw new ApiException(TaskErrorCode.INVALID_TASK_TARGET);
        }
        WorkerTaskContext worker = requireWorker(command.workerId(), companyId);
        if (!worker.canReceiveNewTask()) {
            throw new ApiException(TaskErrorCode.WORKER_NOT_ELIGIBLE);
        }
        return worker;
    }

    private Task requireTask(UUID taskId, UUID companyId) {
        return taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
    }

    private void requireRead(ActorContext actor) {
        actorAuthorizer.requireAnyRole(
                actor,
                UserRole.ADMIN,
                UserRole.HR,
                UserRole.VIEWER
        );
    }

    private void recordTransitionIfChanged(
            Task task,
            TaskStatus previous,
            UUID actorId,
            String reason,
            RequestMetadata metadata,
            Instant now
    ) {
        if (previous == task.status()) {
            return;
        }
        transitionRecorder.record(
                uuidGenerator.generate(),
                task.taskId(),
                task.companyId(),
                previous,
                task.status(),
                actorId,
                reason,
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

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }

    private int rolePriority(UserRole role) {
        return switch (role) {
            case ADMIN -> 0;
            case HR -> 1;
            case VIEWER -> 2;
        };
    }
}
