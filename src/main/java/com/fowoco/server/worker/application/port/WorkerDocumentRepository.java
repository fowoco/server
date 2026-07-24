package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.Optional;
import java.util.UUID;

public interface WorkerDocumentRepository {

    void insert(WorkerDocument document);

    Optional<WorkerDocument> findByIdAndWorkerIdAndCompanyId(
            UUID workerDocumentId,
            UUID workerId,
            UUID companyId
    );

    WorkerDocument update(WorkerDocument document);
}
