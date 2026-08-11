package com.fowoco.server.task.application.renewal;

import java.util.UUID;

public record GeneratedDocumentResult(
        String templateId,
        String format,
        String status,
        UUID storedFileId,
        UUID workerDocumentId
) {
}
