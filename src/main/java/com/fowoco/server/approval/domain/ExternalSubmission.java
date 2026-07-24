package com.fowoco.server.approval.domain;

import java.time.Instant;
import java.util.UUID;

public record ExternalSubmission(
        UUID externalSubmissionId,
        UUID taskId,
        UUID companyId,
        String destination,
        String safeReference,
        UUID submittedBy,
        Instant submittedAt,
        Instant createdAt
) {
}
