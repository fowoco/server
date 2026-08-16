package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerDocumentRepository {

    void insert(WorkerDocument document);

    Optional<WorkerDocument> findByIdAndWorkerIdAndCompanyId(
            UUID workerDocumentId,
            UUID workerId,
            UUID companyId
    );

    Optional<WorkerDocument> findByIdAndCompanyId(UUID workerDocumentId, UUID companyId);

    Optional<WorkerDocument> findByIdAndCompanyIdIncludingArchived(UUID workerDocumentId, UUID companyId);

    WorkerDocument update(WorkerDocument document);

    List<WorkerDocument> findPage(UUID companyId, WorkerDocumentSearchQuery query);

    long countPage(UUID companyId, WorkerDocumentSearchQuery query);
}
