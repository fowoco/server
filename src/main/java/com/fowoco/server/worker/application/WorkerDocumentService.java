package com.fowoco.server.worker.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerDocumentService {

    private final WorkerDocumentRepository workerDocumentRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerDocumentService(
            WorkerDocumentRepository workerDocumentRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerDocumentRepository = workerDocumentRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerDocument register(WorkerDocumentCreateCommand command) {
        WorkerDocument document = WorkerDocument.create(
                uuidGenerator.generate(),
                command.workerId(),
                command.companyId(),
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
    public WorkerDocument findDetail(UUID workerDocumentId, UUID workerId, UUID companyId) {
        return workerDocumentRepository.findByIdAndWorkerIdAndCompanyId(workerDocumentId, workerId, companyId)
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_DOCUMENT_NOT_FOUND));
    }

    @Transactional
    public WorkerDocument patch(WorkerDocumentPatchCommand command) {
        WorkerDocument existing = findDetail(
                command.workerDocumentId(),
                command.workerId(),
                command.companyId()
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
                clock.instant(),
                existing.version()
        );

        workerDocumentRepository.update(updated);
        return updated;
    }

    private static <T> T orElseKeep(T newValue, T existingValue) {
        return newValue != null ? newValue : existingValue;
    }
}
