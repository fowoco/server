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
import com.fowoco.server.workflow.domain.WorkflowCaseTemplate;
import com.fowoco.server.workflow.domain.WorkflowCaseTemplate.ActivationMode;
import com.fowoco.server.workflow.domain.WorkflowCaseTemplate.TaskTemplate;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
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
        List<WorkflowCaseTemplate> matchingTemplates = catalog
                .findCaseTemplatesByIntent(command.detectedIntent())
                .stream()
                .filter(template -> template.workflowIds().contains(command.candidateWorkflowId()))
                .toList();
        if (matchingTemplates.size() != 1) {
            throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        }
        WorkflowCaseTemplate caseTemplate = matchingTemplates.get(0);
        Map<String, WorkflowDefinition> workflows = catalog.workflows().stream()
                .filter(workflow -> caseTemplate.workflowIds().contains(workflow.workflowId()))
                .collect(java.util.stream.Collectors.toMap(
                        WorkflowDefinition::workflowId,
                        workflow -> workflow
                ));
        if (!workflows.keySet().containsAll(caseTemplate.workflowIds())) {
            throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        }
        WorkerIdentityDocumentStatuses identityDocumentStatuses = identityDocumentStatusReader
                .findCurrentStatuses(actor.companyId(), command.workerId());
        List<DocumentType> missingIdentityDocuments = missingIdentityDocuments(
                identityDocumentStatuses
        );
        List<TaskPlan> plans = plans(
                caseTemplate,
                workflows,
                identityDocumentStatuses,
                missingIdentityDocuments
        );
        EnumSet<TaskType> plannedTaskTypes = plans.stream()
                .map(TaskPlan::taskType)
                .filter(EXPIRY_RENEWAL_TASK_TYPES::contains)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(TaskType.class)));
        if (!plannedTaskTypes.equals(EXPIRY_RENEWAL_TASK_TYPES)) {
            throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        }

        LocalDate dueDate = dueDate(command.extractedSlots(), worker);
        Instant now = clock.instant();
        UUID caseId = uuidGenerator.generate();
        List<PlannedTask> plannedTasks = plans.stream()
                .map(plan -> new PlannedTask(plan, uuidGenerator.generate()))
                .toList();
        Map<String, UUID> taskIds = plannedTasks.stream().collect(java.util.stream.Collectors.toMap(
                plannedTask -> plannedTask.plan().template().key(),
                PlannedTask::taskId
        ));

        List<TaskCaseRegistrar.CaseTask> caseTasks = plannedTasks.stream()
                .map(plannedTask -> createTask(
                        plannedTask.plan(),
                        plannedTask.taskId(),
                        command,
                        actor,
                        catalog.bundleVersion(),
                        caseTemplate,
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

    private List<TaskPlan> plans(
            WorkflowCaseTemplate caseTemplate,
            Map<String, WorkflowDefinition> workflows,
            WorkerIdentityDocumentStatuses statuses,
            List<DocumentType> missingIdentityDocuments
    ) {
        return caseTemplate.tasks().stream()
                .filter(template -> active(template, statuses))
                .map(template -> {
                    WorkflowDefinition workflow = workflows.get(template.workflowId());
                    if (workflow == null || !workflow.supportedTaskTypes().contains(template.taskType())) {
                        throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
                    }
                    List<DocumentType> documents = template.taskType() == TaskType.DOCUMENT_REQUEST
                            ? missingIdentityDocuments
                            : List.of();
                    return new TaskPlan(template, effectiveWorkflow(workflow, template), documents);
                })
                .sorted(Comparator.comparingInt(plan -> plan.template().order()))
                .toList();
    }

    private boolean active(TaskTemplate template, WorkerIdentityDocumentStatuses statuses) {
        if (template.activation().mode() == ActivationMode.ALWAYS) {
            return true;
        }
        if (template.activation().mode() != ActivationMode.MISSING_ANY) {
            throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        }
        return template.activation().fieldKeys().stream().anyMatch(field -> switch (field) {
            case "passport_status" -> statuses.passportStatus() == SubmissionStatus.MISSING;
            case "arc_status" -> statuses.arcStatus() == SubmissionStatus.MISSING;
            default -> throw new ApiException(TaskErrorCode.WORKFLOW_TASK_TYPE_MISMATCH);
        });
    }

    private WorkflowDefinition effectiveWorkflow(
            WorkflowDefinition workflow,
            TaskTemplate template
    ) {
        return new WorkflowDefinition(
                workflow.workflowId(),
                workflow.name(),
                workflow.intent(),
                workflow.sensitivity(),
                workflow.supportedTaskTypes(),
                workflow.requiredSlots(),
                workflow.allowedSlotKeys(),
                workflow.resolvableSlotKeys(),
                template.checklistItems(),
                template.completionEvidence(),
                workflow.sourceIds()
        );
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
            WorkflowCaseTemplate caseTemplate,
            UUID caseId,
            Map<String, UUID> taskIds,
            WorkerTaskContext worker,
            LocalDate dueDate,
            Instant now
    ) {
        TaskType taskType = plan.taskType();
        Map<String, Object> businessData = businessData(command, caseTemplate, plan, taskIds);
        List<String> missingSlots = missingRequiredSlots(
                plan.workflow(),
                worker,
                dueDate,
                businessData
        );
        if (!missingSlots.isEmpty()) {
            throw new ApiException(TaskErrorCode.INVALID_AI_CANDIDATE_TASK_DATA);
        }
        String title = plan.template().title();
        String description = plan.template().description();
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
            WorkflowCaseTemplate caseTemplate,
            TaskPlan plan,
            Map<String, UUID> taskIds
    ) {
        TaskType taskType = plan.taskType();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ai_run_id", command.aiRunId().toString());
        data.put("ai_candidate_id", command.candidateId().toString());
        data.put("source_intent", command.detectedIntent());
        data.put("case_template_id", caseTemplate.caseTemplateId());
        data.put("case_title", caseTemplate.name());
        data.put("task_template_key", plan.template().key());
        data.put("candidate_order", plan.template().order());
        List<String> dependencyTaskIds = java.util.stream.Stream.concat(
                        plan.template().dependsOn().stream(),
                        plan.template().dependsOnIfPresent().stream()
                )
                .map(taskIds::get)
                .filter(java.util.Objects::nonNull)
                .map(UUID::toString)
                .toList();
        if (!dependencyTaskIds.isEmpty()) {
            data.put("depends_on_task_ids", dependencyTaskIds);
            data.put("depends_on_task_id", dependencyTaskIds.get(0));
        }
        switch (taskType) {
            case RECONTRACT -> data.put("approval_required", true);
            case STAY_PERIOD_EXTENSION -> data.put("submission_due_offset_days", 7);
            case EMPLOYMENT_PERIOD_EXTENSION -> {
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
            try {
                return LocalDateTime.parse(value).toLocalDate();
            } catch (DateTimeParseException localDateTimeFailure) {
                return OffsetDateTime.parse(value).toLocalDate();
            }
        }
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
            TaskTemplate template,
            WorkflowDefinition workflow,
            List<DocumentType> requestedDocumentTypes
    ) {
        private TaskPlan {
            requestedDocumentTypes = List.copyOf(requestedDocumentTypes);
        }

        private TaskType taskType() {
            return template.taskType();
        }
    }

    private record PlannedTask(TaskPlan plan, UUID taskId) {
    }
}
