package com.fowoco.server.worker.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.file.application.port.StoredFileRepository;
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
    private final StoredFileRepository storedFileRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerDocumentService(
            WorkerDocumentRepository workerDocumentRepository,
            StoredFileRepository storedFileRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerDocumentRepository = workerDocumentRepository;
        this.storedFileRepository = storedFileRepository;
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
                DatabaseTimestamp.now(clock)
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

        UUID resolvedFileId = resolveFileId(command.fileId(), command.companyId(), existing.fileId());

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
                DatabaseTimestamp.nowNotBefore(clock, existing.createdAt()),
                existing.version()
        );

        return workerDocumentRepository.update(updated);
    }

    /**
     * fileId가 요청에 포함되면, 그 파일이 같은 사업장 소속으로 실제 존재하는지 검증한 뒤에만
     * 연결을 허용한다
     */
    private UUID resolveFileId(UUID requestedFileId, UUID companyId, UUID existingFileId) {
        if (requestedFileId == null) {
            return existingFileId;
        }
        storedFileRepository.findByIdAndCompanyId(requestedFileId, companyId)
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_DOCUMENT_FILE_NOT_FOUND));
        return requestedFileId;
    }

    private static <T> T orElseKeep(T newValue, T existingValue) {
        return newValue != null ? newValue : existingValue;
    }
}
