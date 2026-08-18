package com.fowoco.server.worker.archive.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkerArchive(
        UUID workerId,
        UUID companyId,
        Instant archivedAt,
        UUID archivedBy,
        String archiveReason,
        long workerVersion
) {
}
