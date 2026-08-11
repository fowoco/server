package com.fowoco.server.task.application.renewal;

import com.fowoco.server.approval.application.ApprovalControlPort;
import com.fowoco.server.aiintegration.application.renewal.RenewalRequestedField;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.TaskContentCodec.EncodedTaskContent;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskChecklistStatusRepository;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.application.port.TaskTransitionRecorder;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.worker.domain.DocumentType;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RenewalExecutionResultApplier {

    private static final String AUDIT_EVENT_VERSION = "1";
    private static final Set<String> IDENTITY_SLOTS = Set.of(
            "passport_number", "alien_registration_number", "nationality", "full_name", "date_of_birth"
    );

    private final TenantDatabaseContext tenantContext;
    private final TaskRepository taskRepository;
    private final TaskChecklistStatusRepository checklistStatusRepository;
    private final DocumentRequestDraftRepository draftRepository;
    private final TaskTransitionRecorder transitionRecorder;
    private final ApprovalControlPort approvalControl;
    private final AuditEventRepository auditRepository;
    private final TaskContentCodec contentCodec;
    private final GeneratedDocumentService generatedDocumentService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    RenewalExecutionResultApplier(
            TenantDatabaseContext tenantContext,
            TaskRepository taskRepository,
            TaskChecklistStatusRepository checklistStatusRepository,
            DocumentRequestDraftRepository draftRepository,
            TaskTransitionRecorder transitionRecorder,
            ApprovalControlPort approvalControl,
            AuditEventRepository auditRepository,
            TaskContentCodec contentCodec,
            GeneratedDocumentService generatedDocumentService,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.tenantContext = tenantContext;
        this.taskRepository = taskRepository;
        this.checklistStatusRepository = checklistStatusRepository;
        this.draftRepository = draftRepository;
        this.transitionRecorder = transitionRecorder;
        this.approvalControl = approvalControl;
        this.auditRepository = auditRepository;
        this.contentCodec = contentCodec;
        this.generatedDocumentService = generatedDocumentService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    RenewalExecutionResult apply(
            UUID taskId,
            long expectedVersion,
            RenewalRunResponse agentResult,
            List<PreparedRenewalDocument> preparedDocuments,
            Map<String, String> submittedSlotAnswers,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        tenantContext.setCompanyIdForCurrentTransaction(actor.companyId());
        Task task = taskRepository.findByIdAndCompanyId(taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
        if (task.version() != expectedVersion) {
            throw new ApiException(TaskErrorCode.CONCURRENT_MODIFICATION);
        }

        List<GeneratedDocumentResult> generatedDocuments = generatedDocumentService.store(
                task.taskId(), task.workerId(), preparedDocuments, actor, metadata
        );

        Map<String, Object> businessData = new LinkedHashMap<>(
                contentCodec.decodeBusinessData(task.businessDataJson())
        );
        mergeRenewalInputs(businessData, submittedSlotAnswers);
        businessData.put("renewal_execution", executionMetadata(agentResult, generatedDocuments));
        EncodedTaskContent encoded = contentCodec.encode(
                task.targetType(),
                task.workerId(),
                task.workflowId(),
                task.taskType().name(),
                task.title(),
                task.description(),
                task.dueDate(),
                businessData
        );
        Instant now = clock.instant();
        TaskStatus previous = task.status();
        Task.UpdateOutcome update = task.updateContent(
                task.title(),
                task.description(),
                encoded.businessDataJson(),
                encoded.criticalFingerprint(),
                task.dueDate(),
                !"out_of_scope".equals(agentResult.scenario())
                        && agentResult.missingSlots().isEmpty()
                        && !checklistStatusRepository.existsIncompleteRequiredItem(
                                task.taskId(), task.companyId()
                        ),
                expectedVersion,
                actor.actorId(),
                now
        );
        Task saved = taskRepository.save(task);
        if (previous != saved.status()) {
            transitionRecorder.record(
                    uuidGenerator.generate(), saved.taskId(), saved.companyId(), previous, saved.status(),
                    actor.actorId(), "Renewal Agent 결과 반영", metadata.requestId(), now
            );
        }
        if (update.approvalInvalidated()) {
            if (saved.status() == TaskStatus.DRAFT) {
                saved = approvalControl.replaceReviewAfterCriticalChange(
                        taskId, actor, "Renewal Agent 실행 결과로 필수정보가 변경됨", now, metadata
                );
            } else {
                approvalControl.invalidateForCriticalChange(
                        taskId, actor, "Renewal Agent 실행 결과로 필수정보가 변경됨", now, metadata
                );
            }
        }
        appendAudit(
                actor, AuditAction.TASK_UPDATED, AuditTargetType.TASK, saved.taskId(),
                "Renewal Agent 결과를 업무카드에 반영함", metadata, now
        );

        DocumentRequestDraft draft = saveWorkerMessageDraft(saved, agentResult, actor, metadata, now);
        return new RenewalExecutionResult(saved, agentResult, generatedDocuments, draft);
    }

    private void mergeRenewalInputs(
            Map<String, Object> businessData,
            Map<String, String> submittedSlotAnswers
    ) {
        if (submittedSlotAnswers.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        Object current = businessData.get("renewal_inputs");
        if (current instanceof Map<?, ?> currentMap) {
            currentMap.forEach((key, value) -> {
                if (key instanceof String stringKey && value != null) {
                    merged.put(stringKey, value);
                }
            });
        }
        merged.putAll(submittedSlotAnswers);
        businessData.put("renewal_inputs", Map.copyOf(merged));
    }

    private Map<String, Object> executionMetadata(
            RenewalRunResponse result,
            List<GeneratedDocumentResult> generatedDocuments
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("request_id", result.requestId().toString());
        metadata.put("intent", result.intent());
        putIfPresent(metadata, "workflow_id", result.workflowId());
        metadata.put("confidence", result.confidence());
        metadata.put("scenario", result.scenario());
        metadata.put("outcome", result.outcome());
        putIfPresent(metadata, "phase", result.phase());
        putIfPresent(metadata, "step", result.step());
        metadata.put("missing_slots", result.missingSlots());
        metadata.put("requested_fields", result.requestedFields().stream()
                .map(this::requestedFieldMetadata)
                .toList());
        metadata.put("case_signals", result.caseSignals());
        metadata.put("generated_documents", generatedDocuments.stream()
                .map(this::generatedDocumentMetadata)
                .toList());
        return Map.copyOf(metadata);
    }

    private Map<String, Object> requestedFieldMetadata(RenewalRequestedField field) {
        return Map.of("key", field.key(), "source_hint", field.sourceHint());
    }

    private Map<String, Object> generatedDocumentMetadata(GeneratedDocumentResult result) {
        return Map.of(
                "template_id", result.templateId(),
                "format", result.format(),
                "status", result.status(),
                "stored_file_id", result.storedFileId().toString(),
                "worker_document_id", result.workerDocumentId().toString()
        );
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private DocumentRequestDraft saveWorkerMessageDraft(
            Task task,
            RenewalRunResponse result,
            ActorContext actor,
            RequestMetadata metadata,
            Instant now
    ) {
        if (!"ask_worker".equals(result.scenario())
                || result.workerRequestMessage() == null
                || result.workerRequestMessage().isBlank()) {
            return null;
        }
        String language = language(result);
        List<DocumentType> documentTypes = requestedDocumentTypes(result);
        DocumentRequestDraft draft = draftRepository
                .findByTaskIdAndCompanyId(task.taskId(), actor.companyId())
                .map(current -> draftRepository.update(current.withUpdatedContent(
                        language, documentTypes, result.workerRequestMessage(), now
                )))
                .orElseGet(() -> {
                    DocumentRequestDraft created = DocumentRequestDraft.create(
                            uuidGenerator.generate(), task.taskId(), actor.companyId(), language,
                            documentTypes, result.workerRequestMessage(), now
                    );
                    draftRepository.insert(created);
                    return created;
                });
        appendAudit(
                actor, AuditAction.DOCUMENT_REQUEST_DRAFT_SAVED,
                AuditTargetType.DOCUMENT_REQUEST_DRAFT, draft.draftId(),
                "Renewal Agent 안내 초안을 저장함", metadata, now
        );
        return draft;
    }

    private String language(RenewalRunResponse result) {
        Object target = result.languageAssistant() == null
                ? null
                : result.languageAssistant().get("target_language");
        return target instanceof String value && !value.isBlank() ? value : "ko";
    }

    private List<DocumentType> requestedDocumentTypes(RenewalRunResponse result) {
        boolean identityRequired = result.missingSlots().stream().anyMatch(IDENTITY_SLOTS::contains)
                || result.caseSignals().contains("REQUEST_IDENTITY_DOCUMENT");
        if (identityRequired) {
            return List.of(DocumentType.PASSPORT_COPY, DocumentType.ARC);
        }
        return List.of(DocumentType.PASSPORT_COPY);
    }

    private void appendAudit(
            ActorContext actor,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String summary,
            RequestMetadata metadata,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(), actor.companyId(), ActorType.HR_USER, actor.actorId(),
                effectiveRole(actor), action, targetType, targetId, metadata.requestId(),
                metadata.traceId(), AUDIT_EVENT_VERSION, summary, now
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
}
