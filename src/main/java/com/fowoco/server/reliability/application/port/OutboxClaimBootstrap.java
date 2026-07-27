package com.fowoco.server.reliability.application.port;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Claims cross-tenant outbox work without exposing event payloads.
 */
public interface OutboxClaimBootstrap {

    List<ClaimResult> claim(
            String owner,
            Duration leaseDuration,
            int batchSize,
            int maxAttempts
    );

    record ClaimResult(UUID eventId, UUID companyId, boolean reviewRequired) {

        public ClaimResult {
            if (eventId == null || companyId == null) {
                throw new IllegalArgumentException("claimed event identifiers must not be null");
            }
        }
    }
}
