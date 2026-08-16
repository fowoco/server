package com.fowoco.server.task.application;

import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.reliability.application.port.DomainEventPublisher;
import com.fowoco.server.task.application.TaskContentCodec.EncodedTaskContent;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.AiCandidateTaskCreator;
import com.fowoco.server.task.application.port.TaskCaseRegistrar;
import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskChecklistItem;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.application.WorkerTaskContext;
import com.fowoco.server.worker.application.WorkerIdentityDocumentStatuses;
import com.fowoco.server.worker.application.port.WorkerIdentityDocumentStatusReader;
import com.fowoco.server.worker.application.port.WorkerTaskContextReader;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import com.fowoco.server.workflow.application.WorkflowCatalogService;
import com.fowoco.server.workflow.domain.WorkflowCatalog;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCandidateTaskCreationService implements AiCandidateTaskCreator {

    private static final String AUDIT_EVENT_VERSION = "1";
    private static final EnumSet<TaskType> EXPIRY_RENEWAL_TASK_TYPES = EnumSet.of(
            TaskType.RECONTRACT,
            TaskType.STAY_PERIOD_EXTENSION,
            TaskType.EMPLOYMENT_PERIOD_EXTENSION
    );

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final TaskRepository taskRepository;
    private final TaskChecklistRepository checklistRepository;
    private final TaskCaseRegistrar taskCaseRegistrar;
    private final WorkerTaskContextReader workerReader;
    private final WorkerIdentityDocumentStatusReader identityDocumentStatusReader;
    private final WorkflowCatalogService catalogService;
    private final AuditEventRepository auditRepository;
    private final DomainEventPublisher eventPublisher;
    private final TaskContentCodec contentCodec;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public AiCandidateTaskCreationService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            TaskRepository taskRepository,
            TaskChecklistRepository checklistRepository,
            TaskCaseRegistrar taskCaseRegistrar,
            WorkerTaskContextReader workerReader,
            WorkerIdentityDocumentStatusReader identityDocumentStatusReader,
            WorkflowCatalogService catalogService,
            AuditEventRepository auditRepository,
            DomainEventPublisher eventPublisher,
            TaskContentCodec contentCodec,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.taskRepository = taskRepository;
        this.checklistRepository = checklistRepository;
        this.taskCaseRegistrar = taskCaseRegistrar;
        this.workerReader = workerReader;
        this.identityDocumentStatusReader = identityDocumentStatusReader;
        this.catalogService = catalogService;
        this.auditRepository = auditRepository;
        this.eventPublisher = eventPublisher;
        this.contentCodec = contentCodec;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreationResult create(
            CreationCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        actorAuthorizer.requireHrWrite(actor);
        WorkerTaskContext worker = workerReader
                .findByIdAndCompanyId(command.workerId(), actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.WORKER_NOT_FOUND));
        if (!worker.canReceiveNewTask()) {
            throw new ApiException(TaskErrorCode.WORKER_NOT_ELIGIBLE);
        }

        WorkflowCatalog catalog = catalogService.getActiveCatalog();
        List<WorkflowDefinition> workflows = catalog.findByIntent(command.detectedIntent());
        if (workflows.isEmpty()
                || workflows.stream().noneMatch(workflow -> workflow.workflowId()
                        .equals(command.candidateWorkflowId()))) {
            throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        }
        List<TaskPlan> renewalPlans = plans(workflows);
        EnumSet<TaskType> plannedTaskTypes = renewalPlans.stream()
                .map(TaskPlan::taskType)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(TaskType.class)));
        if (!plannedTaskTypes.equals(EXPIRY_RENEWAL_TASK_TYPES)) {
            throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        }

        List<TaskPlan> plans = new ArrayList<>(renewalPlans);
        List<DocumentType> missingIdentityDocuments = missingIdentityDocuments(
                identityDocumentStatusReader.findCurrentStatuses(actor.companyId(), command.workerId())
        );
        if (!missingIdentityDocuments.isEmpty()) {
            plans.add(new TaskPlan(
                    TaskType.DOCUMENT_REQUEST,
                    documentRequestWorkflow(catalog),
                    missingIdentityDocuments
            ));
        }

        LocalDate dueDate = dueDate(command.extractedSlots(), worker);
        Instant now = clock.instant();
        UUID caseId = uuidGenerator.generate();
        List<PlannedTask> plannedTasks = plans.stream()
                .map(plan -> new PlannedTask(plan, uuidGenerator.generate()))
                .toList();
        Map<TaskType, UUID> taskIds = new EnumMap<>(TaskType.class);
        plannedTasks.stream()
                .filter(plannedTask -> EXPIRY_RENEWAL_TASK_TYPES.contains(plannedTask.plan().taskType()))
                .forEach(plannedTask -> taskIds.put(plannedTask.plan().taskType(), plannedTask.taskId()));

        List<TaskCaseRegistrar.CaseTask> caseTasks = plannedTasks.stream()
                .map(plannedTask -> createTask(
                        plannedTask.plan(),
                        plannedTask.taskId(),
                        command,
                        actor,
                        catalog.bundleVersion(),
                        caseId,
                        taskIds,
                        worker,
                        dueDate,
                        now
                ))
                .toList();
        taskCaseRegistrar.registerComposite(caseTasks, LocalDate.now(clock));

        List<UUID> createdTaskIds = new ArrayList<>();
        for (TaskCaseRegistrar.CaseTask caseTask : caseTasks) {
            Task saved = taskRepository.save(caseTask.task());
            checklistRepository.saveAll(caseTask.workflow().checklistItems().stream()
                    .map(template -> TaskChecklistItem.create(
                            uuidGenerator.generate(),
                            saved.taskId(),
                            saved.companyId(),
                            template.itemCode(),
                            template.label(),
                            template.required(),
                            now
                    ))
                    .toList());
            appendAudit(saved, actor, metadata, now);
            eventPublisher.publish(TaskDomainEvents.taskCreated(
                    uuidGenerator.generate(),
                    saved,
                    actor,
                    metadata,
                    now
            ));
            createdTaskIds.add(saved.taskId());
        }
        return new CreationResult(caseId, createdTaskIds);
    }

    private List<TaskPlan> plans(List<WorkflowDefinition> workflows) {
        Map<TaskType, WorkflowDefinition> workflowByType = new EnumMap<>(TaskType.class);
        workflows.forEach(workflow -> workflow.supportedTaskTypes().forEach(taskType -> {
            if (workflowByType.putIfAbsent(taskType, workflow) != null) {
                throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
            }
        }));
        return workflowByType.entrySet().stream()
                .map(entry -> new TaskPlan(entry.getKey(), entry.getValue(), List.of()))
                .sorted(Comparator.comparingInt(this::order))
                .toList();
    }

    private WorkflowDefinition documentRequestWorkflow(WorkflowCatalog catalog) {
        return catalog.findByIntent("DOCUMENT_REQUEST").stream()
                .filter(workflow -> workflow.supportedTaskTypes().contains(TaskType.DOCUMENT_REQUEST))
                .findFirst()
                .orElseThrow(() -> new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH));
    }

    private List<DocumentType> missingIdentityDocuments(WorkerIdentityDocumentStatuses statuses) {
        List<DocumentType> missing = new ArrayList<>();
        if (statuses.passportStatus() == SubmissionStatus.MISSING) {
            missing.add(DocumentType.PASSPORT_COPY);
        }
        if (statuses.arcStatus() == SubmissionStatus.MISSING) {
            missing.add(DocumentType.ARC);
        }
        return List.copyOf(missing);
    }

    private TaskCaseRegistrar.CaseTask createTask(
            TaskPlan plan,
            UUID taskId,
            CreationCommand command,
            ActorContext actor,
            String catalogVersion,
            UUID caseId,
            Map<TaskType, UUID> taskIds,
            WorkerTaskContext worker,
            LocalDate dueDate,
            Instant now
    ) {
        TaskType taskType = plan.taskType();
        Map<String, Object> businessData = businessData(command, plan, taskIds);
        List<String> missingSlots = missingRequiredSlots(
                plan.workflow(),
                worker,
                dueDate,
                businessData
        );
        if (!missingSlots.isEmpty()) {
            throw new ApiException(TaskErrorCode.INVALID_AI_CANDIDATE_TASK_DATA);
        }
        String title = title(plan);
        String description = description(plan);
        EncodedTaskContent content = contentCodec.encode(
                command.workerId(),
                plan.workflow().workflowId(),
                taskType.name(),
                title,
                description,
                dueDate,
                businessData
        );
        Task task = Task.create(
                taskId,
                actor.companyId(),
                command.workerId(),
                caseId,
                taskType,
                plan.workflow().workflowId(),
                catalogVersion,
                title,
                description,
                content.businessDataJson(),
                content.criticalFingerprint(),
                TaskSource.AI_CANDIDATE,
                TaskStatus.DRAFT,
                dueDate,
                actor.actorId(),
                now
        );
        return new TaskCaseRegistrar.CaseTask(task, plan.workflow());
    }

    private Map<String, Object> businessData(
            CreationCommand command,
            TaskPlan plan,
            Map<TaskType, UUID> taskIds
    ) {
        TaskType taskType = plan.taskType();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ai_run_id", command.aiRunId().toString());
        data.put("ai_candidate_id", command.candidateId().toString());
        data.put("source_intent", command.detectedIntent());
        data.put("candidate_order", order(plan));
        switch (taskType) {
            case RECONTRACT -> data.put("approval_required", true);
            case STAY_PERIOD_EXTENSION -> data.put("submission_due_offset_days", 7);
            case EMPLOYMENT_PERIOD_EXTENSION -> {
                data.put("depends_on_task_id", taskIds.get(TaskType.RECONTRACT).toString());
                data.put("dependency_reason", "SIGNED_CONTRACT_REQUIRED");
            }
            case DOCUMENT_REQUEST -> {
                List<String> documentTypes = plan.requestedDocumentTypes().stream()
                        .map(DocumentType::name)
                        .toList();
                data.put("document_type", documentTypes.get(0));
                data.put("requested_document_types", documentTypes);
                data.put("submission_channel", "SECURE_LINK");
                data.put("approval_required", true);
            }
            default -> throw new ApiException(TaskErrorCode.INVALID_AI_CANDIDATE_TASK_DATA);
        }
        return Map.copyOf(data);
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
                case "worker_id" -> worker.workerId() != null;
                case "due_at", "due_date" -> dueDate != null;
                case "contract_start_date" -> worker.contractStartDate() != null;
                case "contract_end_date" -> worker.contractEndDate() != null;
                case "stay_expiry_date" -> worker.stayExpiryDate() != null;
                default -> businessData.get(slot) != null;
            };
            if (!present) {
                missing.add(slot);
            }
        });
        return List.copyOf(missing);
    }

    private LocalDate dueDate(
            Map<String, String> extractedSlots,
            WorkerTaskContext worker
    ) {
        String value = extractedSlots.get("due_at");
        if (value == null || value.isBlank()) {
            value = extractedSlots.get("due_date");
        }
        try {
            if (value != null && !value.isBlank()) {
                return parseDueDate(value);
            }
            if (worker.stayExpiryDate() != null) {
                return worker.stayExpiryDate();
            }
            return worker.contractEndDate();
        } catch (DateTimeParseException exception) {
            throw new ApiException(TaskErrorCode.INVALID_AI_CANDIDATE_TASK_DATA);
        }
    }

    private LocalDate parseDueDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException dateOnlyFailure) {
            return OffsetDateTime.parse(value).toLocalDate();
        }
    }

    private int order(TaskPlan plan) {
        return switch (plan.taskType()) {
            case RECONTRACT -> 1;
            case STAY_PERIOD_EXTENSION -> 2;
            case EMPLOYMENT_PERIOD_EXTENSION -> 3;
            case DOCUMENT_REQUEST -> 4;
            default -> throw new ApiException(TaskErrorCode.INVALID_AI_CANDIDATE_TASK_DATA);
        };
    }

    private String title(TaskPlan plan) {
        return switch (plan.taskType()) {
            case RECONTRACT -> "재계약 조건 확인";
            case STAY_PERIOD_EXTENSION -> "체류기간 연장 준비";
            case EMPLOYMENT_PERIOD_EXTENSION -> "취업활동기간 연장 준비";
            case DOCUMENT_REQUEST -> identityDocumentLabel(plan.requestedDocumentTypes()) + " 사본 요청";
            default -> throw new ApiException(TaskErrorCode.INVALID_AI_CANDIDATE_TASK_DATA);
        };
    }

    private String description(TaskPlan plan) {
        return switch (plan.taskType()) {
            case RECONTRACT -> "재계약 조건과 계속 고용 의사를 검토합니다.";
            case STAY_PERIOD_EXTENSION -> "체류기간 연장에 필요한 정보와 서류를 확인합니다.";
            case EMPLOYMENT_PERIOD_EXTENSION -> "재계약 결과를 바탕으로 취업활동기간 연장을 준비합니다.";
            case DOCUMENT_REQUEST -> "근로자에게 " + identityDocumentLabel(plan.requestedDocumentTypes())
                    + " 사본 제출을 요청합니다.";
            default -> throw new ApiException(TaskErrorCode.INVALID_AI_CANDIDATE_TASK_DATA);
        };
    }

    private String identityDocumentLabel(List<DocumentType> documentTypes) {
        return documentTypes.stream()
                .map(documentType -> switch (documentType) {
                    case PASSPORT_COPY -> "여권";
                    case ARC -> "외국인등록증";
                    case CONTRACT -> "근로계약서";
                    case PERMIT -> "고용허가서";
                    case EMPLOYMENT_EXTENSION_APPLICATION -> "취업활동기간 연장신청서";
                    case INTEGRATED_APPLICATION -> "통합신청서";
                    case RESIDENCE_PROOF -> "체류지 입증자료";
                })
                .collect(java.util.stream.Collectors.joining("·"));
    }

    private void appendAudit(
            Task task,
            ActorContext actor,
            RequestMetadata metadata,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                task.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                AuditAction.TASK_CREATED,
                AuditTargetType.TASK,
                task.taskId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "AI 후보를 채택하여 업무카드를 생성함",
                now
        ));
    }

    private UserRole effectiveRole(ActorContext actor) {
        return actor.roles().stream()
                .min(Comparator.comparingInt(role -> switch (role) {
                    case ADMIN -> 0;
                    case HR -> 1;
                    case VIEWER -> 2;
                }))
                .orElseThrow();
    }

    private record TaskPlan(
            TaskType taskType,
            WorkflowDefinition workflow,
            List<DocumentType> requestedDocumentTypes
    ) {
        private TaskPlan {
            requestedDocumentTypes = List.copyOf(requestedDocumentTypes);
        }
    }

    private record PlannedTask(TaskPlan plan, UUID taskId) {
    }
}
