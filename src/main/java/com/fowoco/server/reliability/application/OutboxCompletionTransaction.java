package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.domain.EventPublication;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxCompletionTransaction {

    private final EventPublicationRepository repository;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxCompletionTransaction(
            EventPublicationRepository repository,
            OutboxMetrics metrics,
            Clock clock
    ) {
        this.repository = repository;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID eventId, String owner) {
        Instant now = clock.instant();
        EventPublication publication = repository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
        publication.complete(owner, now);
        repository.save(publication);
        metrics.recordCompleted();
    }
}
