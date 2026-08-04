package com.fowoco.server.workerlink.application;

import com.fowoco.server.file.domain.StoredFile;
import java.time.Instant;

public record WorkerLinkDocumentUploadResult(StoredFile storedFile, Instant linkExpiresAt) {
}
