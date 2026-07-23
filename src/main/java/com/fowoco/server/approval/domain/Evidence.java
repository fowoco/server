package com.fowoco.server.approval.domain;

import java.time.Instant;
import java.util.UUID;

public record Evidence(
        UUID evidenceId,
        UUID taskId,
        UUID companyId,
        EvidenceType evidenceType,
        String fileReference,
        String note,
        UUID recordedBy,
        Instant recordedAt,
        Instant createdAt
) {
}
