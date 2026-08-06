package com.fowoco.server.document.application;

import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.worker.domain.WorkerDocument;

public record DocumentDetailResult(WorkerDocument document, String workerDisplayName, StoredFile storedFile) {
}
