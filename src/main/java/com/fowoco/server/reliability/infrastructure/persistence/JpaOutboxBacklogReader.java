package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.application.port.OutboxBacklogReader;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "transaction-only",
        matchIfMissing = true
)
public class JpaOutboxBacklogReader implements OutboxBacklogReader {

    private final EventPublicationRepository repository;

    public JpaOutboxBacklogReader(EventPublicationRepository repository) {
        this.repository = repository;
    }

    @Override
    public long countOutstanding() {
        return repository.countOutstanding();
    }

    @Override
    public Optional<Instant> findOldestOutstandingOccurredAt() {
        return repository.findOldestOutstandingOccurredAt();
    }
}
