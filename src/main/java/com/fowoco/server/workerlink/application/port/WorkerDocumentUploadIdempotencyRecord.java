package com.fowoco.server.workerlink.application.port;

import java.util.Objects;
import java.util.UUID;

public record WorkerDocumentUploadIdempotencyRecord(UUID storedFileId, String requestHash) {

    public WorkerDocumentUploadIdempotencyRecord {
        Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
    }
}
