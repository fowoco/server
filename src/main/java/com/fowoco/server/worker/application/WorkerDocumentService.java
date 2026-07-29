package com.fowoco.server.worker.application;

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
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerDocumentService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final WorkerDocumentRepository workerDocumentRepository;
    private final WorkerRepository workerRepository;
    private final StoredFileRepository storedFileRepository;
    private final AuditEventRepository auditRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerDocumentService(
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            StoredFileRepository storedFileRepository,
            AuditEventRepository auditRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerDocumentRepository = workerDocumentRepository;
        this.workerRepository = workerRepository;
        this.storedFileRepository = storedFileRepository;
        this.auditRepository = auditRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerDocument register(WorkerDocumentCreateCommand command, ActorContext actor) {
        bindTenant(actor);
        workerRepository.findByWorkerIdAndCompanyId(command.workerId(), actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_NOT_FOUND));

        WorkerDocument document = WorkerDocument.create(
                uuidGenerator.generate(),
                command.workerId(),
                actor.companyId(),
                command.documentType(),
                command.submissionStatus(),
                command.expiryDate(),
                command.destination(),
                command.note(),
                DatabaseTimestamp.now(clock)
        );
        workerDocumentRepository.insert(document);
        return document;
    }

    @Transactional(readOnly = true)
    public WorkerDocument findDetail(
            UUID workerDocumentId,
            UUID workerId,
            ActorContext actor
    ) {
        bindTenant(actor);
        return workerDocumentRepository.findByIdAndWorkerIdAndCompanyId(
                        workerDocumentId,
                        workerId,
                        actor.companyId()
                )
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_DOCUMENT_NOT_FOUND));
    }

    @Transactional
    public WorkerDocument patch(
            WorkerDocumentPatchCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        WorkerDocument existing = findDetail(
                command.workerDocumentId(),
                command.workerId(),
                actor
        );
        if (existing.version() != command.expectedVersion()) {
            throw new ApiException(WorkerErrorCode.WORKER_DOCUMENT_VERSION_CONFLICT);
        }

        UUID resolvedFileId = resolveFileId(command.fileId(), actor.companyId(), existing.fileId());
        boolean fileNewlyLinked = command.fileId() != null && !command.fileId().equals(existing.fileId());

        Instant now = DatabaseTimestamp.nowNotBefore(clock, existing.createdAt());
        WorkerDocument updated = new WorkerDocument(
                existing.workerDocumentId(),
                existing.workerId(),
                existing.companyId(),
                orElseKeep(command.documentType(), existing.documentType()),
                orElseKeep(command.submissionStatus(), existing.submissionStatus()),
                orElseKeep(command.expiryDate(), existing.expiryDate()),
                orElseKeep(command.destination(), existing.destination()),
                orElseKeep(command.note(), existing.note()),
                resolvedFileId,
                existing.createdAt(),
                now,
                existing.version()
        );

        WorkerDocument saved = workerDocumentRepository.update(updated);

        if (fileNewlyLinked) {
            appendAudit(
                    actor,
                    AuditAction.WORKER_DOCUMENT_FILE_LINKED,
                    AuditTargetType.WORKER_DOCUMENT,
                    saved.workerDocumentId(),
                    "서류에 파일 연결: file_id=" + resolvedFileId,
                    metadata,
                    now
            );
        }

        return saved;
    }

    private UUID resolveFileId(UUID requestedFileId, UUID companyId, UUID existingFileId) {
        if (requestedFileId == null) {
            return existingFileId;
        }
        storedFileRepository.findByIdAndCompanyId(requestedFileId, companyId)
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_DOCUMENT_FILE_NOT_FOUND));
        return requestedFileId;
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

    private static <T> T orElseKeep(T newValue, T existingValue) {
        return newValue != null ? newValue : existingValue;
    }

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }
}
