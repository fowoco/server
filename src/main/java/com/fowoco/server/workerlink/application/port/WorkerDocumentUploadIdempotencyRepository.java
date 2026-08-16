package com.fowoco.server.workerlink.application.port;

import java.util.Optional;
import java.util.UUID;

public interface WorkerDocumentUploadIdempotencyRepository {

    Optional<WorkerDocumentUploadIdempotencyRecord> findByKeyHash(
            UUID workerLinkId,
            UUID companyId,
            String idempotencyKeyHash
    );

    void save(
            UUID workerLinkId,
            UUID companyId,
            String idempotencyKeyHash,
            String requestHash,
            UUID storedFileId
    );
}
