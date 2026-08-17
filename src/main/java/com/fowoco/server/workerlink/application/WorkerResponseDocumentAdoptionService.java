package com.fowoco.server.workerlink.application;

import com.fowoco.server.approval.application.ApprovalControlPort;
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
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.reliability.application.port.DomainEventPublisher;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.application.port.TaskTransitionRecorder;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentFileLookup;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import com.fowoco.server.worker.domain.WorkerDocument;
import com.fowoco.server.worker.domain.WorkerDocumentSource;
import com.fowoco.server.workerlink.application.WorkerResponseDocumentAdoptionResult.AdoptedDocument;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerResponseDocumentAdoptionService {

    private static final String AUDIT_EVENT_VERSION = "1";
    private static final String ADOPTION_NOTE = "근로자 모바일 링크 제출 자료를 HR이 채택함";

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final TaskRepository taskRepository;
    private final TaskTransitionRecorder transitionRecorder;
    private final WorkerResponseRepository workerResponseRepository;
    private final WorkerLinkRepository workerLinkRepository;
    private final StoredFileRepository storedFileRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final WorkerDocumentFileLookup workerDocumentFileLookup;
    private final DocumentRequestDraftRepository documentRequestDraftRepository;
    private final ApprovalControlPort approvalControl;
    private final AuditEventRepository auditRepository;
    private final DomainEventPublisher eventPublisher;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerResponseDocumentAdoptionService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            TaskRepository taskRepository,
            TaskTransitionRecorder transitionRecorder,
            WorkerResponseRepository workerResponseRepository,
            WorkerLinkRepository workerLinkRepository,
            StoredFileRepository storedFileRepository,
            WorkerDocumentRepository workerDocumentRepository,
            WorkerDocumentFileLookup workerDocumentFileLookup,
            DocumentRequestDraftRepository documentRequestDraftRepository,
            ApprovalControlPort approvalControl,
            AuditEventRepository auditRepository,
            DomainEventPublisher eventPublisher,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.taskRepository = taskRepository;
        this.transitionRecorder = transitionRecorder;
        this.workerResponseRepository = workerResponseRepository;
        this.workerLinkRepository = workerLinkRepository;
        this.storedFileRepository = storedFileRepository;
        this.workerDocumentRepository = workerDocumentRepository;
        this.workerDocumentFileLookup = workerDocumentFileLookup;
        this.documentRequestDraftRepository = documentRequestDraftRepository;
        this.approvalControl = approvalControl;
        this.auditRepository = auditRepository;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerResponseDocumentAdoptionResult adopt(
            UUID taskId,
            UUID responseId,
            long expectedTaskVersion,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        actorAuthorizer.requireHrWrite(actor);
        Task task = taskRepository.findByIdAndCompanyId(taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
        if (task.workerId() == null) {
            throw new ApiException(WorkerLinkErrorCode.TASK_WORKER_TARGET_REQUIRED);
        }

        WorkerResponseRepository.WorkerResponseItem responseItem = workerResponseRepository
                .findByResponseIdAndTaskIdAndCompanyId(responseId, taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_RESPONSE_NOT_FOUND));
        if (responseItem.response().responseType() != WorkerResponseType.DOCUMENT_SUBMITTED) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_RESPONSE_NOT_DOCUMENT_SUBMISSION);
        }
        if (responseItem.uploadIds().isEmpty()) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_RESPONSE_DOCUMENTS_INCOMPLETE);
        }

        Instant now = DatabaseTimestamp.now(clock);
        List<AdoptedDocument> adoptedDocuments = new ArrayList<>();
        List<WorkerDocument> newlyAdoptedDocuments = new ArrayList<>();
        Set<DocumentType> submittedTypes = EnumSet.noneOf(DocumentType.class);
        int newlyAdoptedCount = 0;
        for (UUID fileId : responseItem.uploadIds()) {
            StoredFile file = requireSubmittedFile(fileId, task, actor.companyId());
            DocumentType documentType = parseDocumentType(file.purpose());
            submittedTypes.add(documentType);
            Optional<WorkerDocument> existing = workerDocumentFileLookup
                    .findByFileIdAndCompanyId(fileId, actor.companyId());
            WorkerDocument document = existing
                    .map(value -> requireSameAdoption(value, task, documentType))
                    .orElseGet(() -> createDocument(task, documentType, fileId, now));
            if (existing.isEmpty()) {
                newlyAdoptedCount++;
                newlyAdoptedDocuments.add(document);
                appendFileAudit(document, actor, metadata, now);
            }
            adoptedDocuments.add(new AdoptedDocument(
                    document.workerDocumentId(),
                    document.fileId(),
                    document.documentType()
            ));
        }

        Set<DocumentType> requiredTypes = documentRequestDraftRepository
                .findByTaskIdAndCompanyId(taskId, actor.companyId())
                .map(draft -> EnumSet.copyOf(draft.documentTypes()))
                .orElseGet(() -> EnumSet.copyOf(submittedTypes));
        Set<DocumentType> adoptedTypes = officiallyAdoptedTypes(task, actor.companyId());
        if (!adoptedTypes.containsAll(requiredTypes)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_RESPONSE_DOCUMENTS_INCOMPLETE);
        }

        Task savedTask = advanceTask(task, expectedTaskVersion, actor, metadata, now);
        closeWorkerConversation(responseItem.response().workerLinkId(), actor.companyId(), now);

        if (newlyAdoptedCount > 0) {
            auditRepository.append(new AuditEvent(
                    uuidGenerator.generate(),
                    actor.companyId(),
                    ActorType.HR_USER,
                    actor.actorId(),
                    effectiveRole(actor),
                    AuditAction.WORKER_LINK_RESPONSES_REVIEWED,
                    AuditTargetType.TASK,
                    taskId,
                    metadata.requestId(),
                    metadata.traceId(),
                    AUDIT_EVENT_VERSION,
                    "근로자 제출 파일을 공식 서류로 채택: " + newlyAdoptedCount + "개",
                    now
            ));
        }
        newlyAdoptedDocuments.stream()
                .filter(this::supportsOcr)
                .forEach(document -> eventPublisher.publish(
                        WorkerResponseDomainEvents.documentAdopted(
                                uuidGenerator.generate(), document, actor, metadata, now
                        )
                ));

        return new WorkerResponseDocumentAdoptionResult(
                responseId,
                adoptedDocuments,
                savedTask.status(),
                savedTask.version()
        );
    }

    private boolean supportsOcr(WorkerDocument document) {
        return document.documentType() == DocumentType.PASSPORT_COPY
                || document.documentType() == DocumentType.ARC;
    }

    private StoredFile requireSubmittedFile(UUID fileId, Task task, UUID companyId) {
        StoredFile file = storedFileRepository.findByIdAndCompanyId(fileId, companyId)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE));
        if (!file.verified() || !Objects.equals(file.taskId(), task.taskId())) {
            throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
        }
        return file;
    }

    private DocumentType parseDocumentType(String value) {
        try {
            return DocumentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_RESPONSE_DOCUMENT_TYPE_INVALID);
        }
    }

    private WorkerDocument requireSameAdoption(
            WorkerDocument existing,
            Task task,
            DocumentType documentType
    ) {
        if (!Objects.equals(existing.workerId(), task.workerId())
                || !Objects.equals(existing.taskId(), task.taskId())
                || existing.documentType() != documentType) {
            throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
        }
        return existing;
    }

    private WorkerDocument createDocument(
            Task task,
            DocumentType documentType,
            UUID fileId,
            Instant now
    ) {
        WorkerDocument document = WorkerDocument.createSubmittedWithFile(
                uuidGenerator.generate(),
                task.workerId(),
                task.companyId(),
                task.taskId(),
                documentType,
                WorkerDocumentSource.WORKER_UPLOAD,
                ADOPTION_NOTE,
                fileId,
                now
        );
        workerDocumentRepository.insert(document);
        return document;
    }

    private Set<DocumentType> officiallyAdoptedTypes(Task task, UUID companyId) {
        Set<DocumentType> result = EnumSet.noneOf(DocumentType.class);
        workerDocumentRepository.findPage(
                        companyId,
                        new WorkerDocumentSearchQuery(task.workerId(), task.taskId(), null, null, null, 0, 100)
                )
                .stream()
                .filter(document -> document.fileId() != null)
                .filter(document -> document.submissionStatus() == SubmissionStatus.SUBMITTED
                        || document.submissionStatus() == SubmissionStatus.VERIFIED)
                .forEach(document -> result.add(document.documentType()));
        return result;
    }

    private Task advanceTask(
            Task task,
            long expectedTaskVersion,
            ActorContext actor,
            RequestMetadata metadata,
            Instant now
    ) {
        if (task.status() == TaskStatus.WAITING_WORKER) {
            if (!approvalControl.hasValidApproval(
                    task.taskId(),
                    task.companyId(),
                    task.contentRevision(),
                    task.criticalFingerprint()
            )) {
                throw new ApiException(WorkerLinkErrorCode.TASK_NOT_APPROVED);
            }
            TaskStatus previous = task.resumeAfterWorkerSubmission(
                    expectedTaskVersion,
                    actor.actorId(),
                    now
            );
            Task saved = taskRepository.save(task);
            transitionRecorder.record(
                    uuidGenerator.generate(),
                    task.taskId(),
                    task.companyId(),
                    previous,
                    saved.status(),
                    actor.actorId(),
                    "근로자 제출 서류를 HR이 채택함",
                    metadata.requestId(),
                    now
            );
            return saved;
        }
        if (task.status() != TaskStatus.APPROVED) {
            throw new ApiException(TaskErrorCode.TASK_TRANSITION_NOT_ALLOWED);
        }
        if (task.version() != expectedTaskVersion) {
            throw new ApiException(TaskErrorCode.CONCURRENT_MODIFICATION);
        }
        return task;
    }

    private void closeWorkerConversation(UUID workerLinkId, UUID companyId, Instant now) {
        WorkerLink link = workerLinkRepository.findByIdAndCompanyId(workerLinkId, companyId)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_RESPONSE_NOT_FOUND));
        workerLinkRepository.update(link.markReviewed(now).revoke(now));
    }

    private void appendFileAudit(
            WorkerDocument document,
            ActorContext actor,
            RequestMetadata metadata,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                AuditAction.WORKER_DOCUMENT_FILE_LINKED,
                AuditTargetType.WORKER_DOCUMENT,
                document.workerDocumentId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "근로자 제출 파일을 공식 서류에 연결",
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
}
