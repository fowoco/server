package com.fowoco.server.worker.archive.application;

import java.util.UUID;

public record WorkerArchiveCommand(
        UUID workerId,
        String reason,
        long expectedVersion
) {
}
