package com.fowoco.server.document.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.error.DocumentErrorCode;
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRequestDraftService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final TaskRepository taskRepository;
    private final DocumentRequestDraftRepository documentRequestDraftRepository;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public DocumentRequestDraftService(
            TaskRepository taskRepository,
            DocumentRequestDraftRepository documentRequestDraftRepository,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.documentRequestDraftRepository = documentRequestDraftRepository;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public DocumentRequestDraft upsert(
            DocumentRequestDraftCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        taskRepository.findByIdAndCompanyId(command.taskId(), command.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));

        Optional<DocumentRequestDraft> existing = documentRequestDraftRepository
                .findByTaskIdAndCompanyId(command.taskId(), command.companyId());

        Instant now = clock.instant();
        DocumentRequestDraft saved;

        if (existing.isEmpty()) {
            DocumentRequestDraft draft = DocumentRequestDraft.create(
                    uuidGenerator.generate(),
                    command.taskId(),
                    command.companyId(),
                    command.language(),
                    command.documentTypes(),
                    command.message(),
                    now
            );
            documentRequestDraftRepository.insert(draft);
            saved = draft;
        } else {
            DocumentRequestDraft current = existing.get();
            if (current.version() != command.expectedVersion()) {
                throw new ApiException(DocumentErrorCode.DOCUMENT_REQUEST_DRAFT_VERSION_CONFLICT);
            }
            DocumentRequestDraft updated = current.withUpdatedContent(
                    command.language(),
                    command.documentTypes(),
                    command.message(),
                    now
            );
            saved = documentRequestDraftRepository.update(updated);
        }

        appendAudit(
                actor,
                AuditAction.DOCUMENT_REQUEST_DRAFT_SAVED,
                AuditTargetType.DOCUMENT_REQUEST_DRAFT,
                saved.draftId(),
                "문서 요청 초안 저장: task_id=" + command.taskId(),
                metadata,
                now
        );

        return saved;
    }

    private void appendAudit(
            ActorContext actor,
            AuditAction action,
            AuditTargetType targetType,
            java.util.UUID targetId,
            String summary,
            RequestMetadata metadata,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                action,
                targetType,
                targetId,
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
}
