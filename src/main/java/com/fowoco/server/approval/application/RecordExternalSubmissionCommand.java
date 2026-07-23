package com.fowoco.server.approval.application;

import java.time.Instant;

public record RecordExternalSubmissionCommand(
        long expectedVersion,
        String destination,
        String safeReference,
        Instant submittedAt
) {
}
