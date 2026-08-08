package com.fowoco.server.workerimport.application;

import java.time.Instant;
import java.util.UUID;

public record WorkerImportCommitRecord(
        UUID companyId,
        UUID importId,
        String idempotencyKeyHash,
        String requestHash,
        WorkerImportView responseSnapshot,
        Instant createdAt
) {
}
