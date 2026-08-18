package com.fowoco.server.worker.archive.application;

import java.util.List;
import java.util.UUID;

public record WorkerArchiveEligibility(
        UUID workerId,
        boolean archivable,
        List<WorkerArchiveBlocker> blockers,
        long workerVersion
) {
    public WorkerArchiveEligibility {
        blockers = List.copyOf(blockers);
    }
}
