package com.fowoco.server.workerlink.application.port;

import java.util.Optional;
import java.util.UUID;

public interface WorkerDocumentUploadIdempotencyRepository {

    Optional<UUID> findStoredFileId(UUID workerLinkId, UUID companyId, String clientRequestId);

    void save(UUID workerLinkId, UUID companyId, String clientRequestId, UUID storedFileId);
}
