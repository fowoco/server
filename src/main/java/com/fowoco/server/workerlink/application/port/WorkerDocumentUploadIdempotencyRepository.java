package com.fowoco.server.workerlink.application.port;

import java.util.Optional;
import java.util.UUID;

public interface WorkerDocumentUploadIdempotencyRepository {

    Optional<UUID> findStoredFileId(UUID workerLinkId, String clientRequestId);

    void save(UUID workerLinkId, String clientRequestId, UUID storedFileId);
}
