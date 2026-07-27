package com.fowoco.server.document.application;

import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DocumentPageResult(
        List<WorkerDocument> items,
        Map<UUID, String> workerDisplayNames,
        int page,
        int size,
        long totalElements
) {
}
