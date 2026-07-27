package com.fowoco.server.reliability.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Reads payload-free, cross-tenant backlog aggregates for operational metrics.
 */
public interface OutboxBacklogReader {

    long countOutstanding();

    Optional<Instant> findOldestOutstandingOccurredAt();
}
