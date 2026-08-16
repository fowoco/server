package com.fowoco.server.workerlink.application;

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
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.worker.application.port.WorkerDocumentFileLookup;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.domain.ConversationStatus;
import com.fowoco.server.workerlink.domain.WorkerLink;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerResponseManagementService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final TaskRepository taskRepository;
    private final WorkerResponseRepository workerResponseRepository;
    private final WorkerLinkRepository workerLinkRepository;
    private final StoredFileRepository storedFileRepository;
    private final WorkerDocumentFileLookup workerDocumentFileLookup;
    private final WorkerResponsePayloadCodec payloadCodec;
    private final AuditEventRepository auditRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerResponseManagementService(
            TaskRepository taskRepository,
            WorkerResponseRepository workerResponseRepository,
            WorkerLinkRepository workerLinkRepository,
            StoredFileRepository storedFileRepository,
            WorkerDocumentFileLookup workerDocumentFileLookup,
            WorkerResponsePayloadCodec payloadCodec,
            AuditEventRepository auditRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.workerResponseRepository = workerResponseRepository;
        this.workerLinkRepository = workerLinkRepository;
        this.storedFileRepository = storedFileRepository;
        this.workerDocumentFileLookup = workerDocumentFileLookup;
        this.payloadCodec = payloadCodec;
        this.auditRepository = auditRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkerResponsePageResult findAll(UUID taskId, int page, int size, ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        requireTask(taskId, actor.companyId());
        WorkerResponseRepository.WorkerResponsePage result = workerResponseRepository
                .findAllByTaskIdAndCompanyId(taskId, actor.companyId(), page, size);
        List<WorkerResponseQueryResult> items = result.items().stream()
                .map(item -> new WorkerResponseQueryResult(
                        item.response().responseId(),
                        item.response().responseType(),
                        item.response().message(),
                        payloadCodec.decodeAnswers(item.response().answersJson()),
                        item.uploadIds(),
                        item.uploadIds().stream()
                                .map(fileId -> toUploadResult(fileId, actor.companyId()))
                                .toList(),
                        item.conversationStatus(),
                        item.conversationStatus() == ConversationStatus.NEEDS_FOLLOWUP,
                        item.response().receivedAt()
                ))
                .toList();
        return new WorkerResponsePageResult(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    private WorkerResponseUploadResult toUploadResult(UUID fileId, UUID companyId) {
        StoredFile file = storedFileRepository.findByIdAndCompanyId(fileId, companyId)
                .orElseThrow(() -> new IllegalStateException("worker response upload file is missing"));
        return new WorkerResponseUploadResult(
                file.storedFileId(),
                file.name(),
                file.mimeType(),
                file.size(),
                parseDocumentType(file.purpose()),
                workerDocumentFileLookup.findByFileIdAndCompanyId(fileId, companyId).isPresent()
        );
    }

    private DocumentType parseDocumentType(String value) {
        try {
            return DocumentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Transactional
    public void markReviewed(UUID taskId, ActorContext actor, RequestMetadata metadata) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        requireTask(taskId, actor.companyId());
        Instant now = DatabaseTimestamp.now(clock);
        List<WorkerLink> unreadLinks = workerLinkRepository
                .findAllByTaskIdAndCompanyId(taskId, actor.companyId())
                .stream()
                .filter(link -> link.conversationStatus() == ConversationStatus.NEEDS_FOLLOWUP)
                .toList();
        unreadLinks.forEach(link -> workerLinkRepository.update(link.markReviewed(
                DatabaseTimestamp.nowNotBefore(clock, link.createdAt())
        )));
        if (unreadLinks.isEmpty()) {
            return;
        }
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
                "근로자 응답 확인: " + unreadLinks.size() + "개 대화",
                now
        ));
    }

    private void requireTask(UUID taskId, UUID companyId) {
        taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
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
