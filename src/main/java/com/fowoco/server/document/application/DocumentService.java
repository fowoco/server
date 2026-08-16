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
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.error.DocumentErrorCode;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final WorkerDocumentRepository workerDocumentRepository;
    private final WorkerRepository workerRepository;
    private final StoredFileRepository storedFileRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final AuditEventRepository auditEventRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public DocumentService(
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            StoredFileRepository storedFileRepository,
            TenantDatabaseContext tenantDatabaseContext,
            AuditEventRepository auditEventRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerDocumentRepository = workerDocumentRepository;
        this.workerRepository = workerRepository;
        this.storedFileRepository = storedFileRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.auditEventRepository = auditEventRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DocumentDetailResult findById(UUID workerDocumentId, ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        WorkerDocument document = workerDocumentRepository
                .findByIdAndCompanyId(workerDocumentId, companyId)
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        String displayName = workerRepository
                .findByWorkerIdAndCompanyId(document.workerId(), companyId)
                .map(Worker::displayName)
                .orElse(null);
        StoredFile storedFile = document.fileId() == null
                ? null
                : storedFileRepository.findByIdAndCompanyId(document.fileId(), companyId).orElse(null);
        return new DocumentDetailResult(document, displayName, storedFile);
    }

    @Transactional(readOnly = true)
    public DocumentPageResult findPage(ActorContext actor, WorkerDocumentSearchQuery query) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        List<WorkerDocument> items = workerDocumentRepository.findPage(companyId, query);
        long totalElements = workerDocumentRepository.countPage(companyId, query);

        Set<UUID> workerIds = items.stream()
                .map(WorkerDocument::workerId)
                .collect(Collectors.toSet());
        Map<UUID, String> workerDisplayNames = workerRepository
                .findAllByWorkerIdsAndCompanyId(workerIds, companyId)
                .stream()
                .collect(Collectors.toMap(Worker::workerId, Worker::displayName));

        return new DocumentPageResult(items, workerDisplayNames, query.page(), query.size(), totalElements);
    }

    @Transactional
    public void archive(
            UUID workerDocumentId,
            long expectedVersion,
            String reason,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        WorkerDocument existing = workerDocumentRepository
                .findByIdAndCompanyIdIncludingArchived(workerDocumentId, actor.companyId())
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        if (existing.isArchived()) {
            return;
        }
        if (existing.version() != expectedVersion) {
            throw new ApiException(DocumentErrorCode.DOCUMENT_VERSION_CONFLICT);
        }

        Instant now = DatabaseTimestamp.nowNotBefore(clock, existing.updatedAt());
        WorkerDocument archived = workerDocumentRepository.update(
                existing.archive(actor.actorId(), reason, now)
        );
        auditEventRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                AuditAction.DOCUMENT_ARCHIVED,
                AuditTargetType.WORKER_DOCUMENT,
                archived.workerDocumentId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "문서 보관 처리",
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
