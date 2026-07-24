package com.fowoco.server.worker.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerDocumentService {

    private final WorkerDocumentRepository workerDocumentRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerDocumentService(
            WorkerDocumentRepository workerDocumentRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerDocumentRepository = workerDocumentRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerDocument register(WorkerDocumentCreateCommand command, ActorContext actor) {
        bindTenant(actor);
        WorkerDocument document = WorkerDocument.create(
                uuidGenerator.generate(),
                command.workerId(),
                actor.companyId(),
                command.documentType(),
                command.submissionStatus(),
                command.expiryDate(),
                command.destination(),
                command.note(),
                clock.instant()
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
    public WorkerDocument patch(WorkerDocumentPatchCommand command, ActorContext actor) {
        bindTenant(actor);
        WorkerDocument existing = findDetail(
                command.workerDocumentId(),
                command.workerId(),
                actor
        );
        if (existing.version() != command.expectedVersion()) {
            throw new ApiException(WorkerErrorCode.WORKER_DOCUMENT_VERSION_CONFLICT);
        }

        WorkerDocument updated = new WorkerDocument(
                existing.workerDocumentId(),
                existing.workerId(),
                existing.companyId(),
                orElseKeep(command.documentType(), existing.documentType()),
                orElseKeep(command.submissionStatus(), existing.submissionStatus()),
                orElseKeep(command.expiryDate(), existing.expiryDate()),
                orElseKeep(command.destination(), existing.destination()),
                orElseKeep(command.note(), existing.note()),
                existing.fileId(),
                existing.createdAt(),
                updateTime(existing.createdAt()),
                existing.version()
        );

        return workerDocumentRepository.update(updated);
    }

    private static <T> T orElseKeep(T newValue, T existingValue) {
        return newValue != null ? newValue : existingValue;
    }

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }

    private Instant updateTime(Instant createdAt) {
        Instant now = clock.instant();
        return now.isBefore(createdAt) ? createdAt : now;
    }
}
