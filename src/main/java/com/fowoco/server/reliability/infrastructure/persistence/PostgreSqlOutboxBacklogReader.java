package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.OutboxBacklogReader;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "postgresql"
)
public class PostgreSqlOutboxBacklogReader implements OutboxBacklogReader {

    private final EntityManager entityManager;

    public PostgreSqlOutboxBacklogReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public long countOutstanding() {
        return ((Number) entityManager.createNativeQuery(
                "SELECT public.bootstrap_count_outstanding_event_publications()"
        ).getSingleResult()).longValue();
    }

    @Override
    public Optional<Instant> findOldestOutstandingOccurredAt() {
        Object result = entityManager.createNativeQuery(
                "SELECT public.bootstrap_oldest_outstanding_event_occurred_at()"
        ).getSingleResult();
        if (result == null) {
            return Optional.empty();
        }
        if (result instanceof Instant instant) {
            return Optional.of(instant);
        }
        if (result instanceof OffsetDateTime offsetDateTime) {
            return Optional.of(offsetDateTime.toInstant());
        }
        throw new IllegalStateException("Unexpected PostgreSQL timestamp mapping.");
    }
}
