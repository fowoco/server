package com.fowoco.server.workerlink.application.port;

import com.fowoco.server.workerlink.domain.WorkerResponse;
import java.util.Optional;
import java.util.UUID;

public interface WorkerResponseRepository {

    void insert(WorkerResponse workerResponse);

    Optional<WorkerResponse> findByWorkerLinkIdAndIdempotencyKey(UUID workerLinkId, String idempotencyKey);

    void linkUpload(UUID responseId, UUID storedFileId);

    boolean isUploadAlreadyLinked(UUID storedFileId);
}
