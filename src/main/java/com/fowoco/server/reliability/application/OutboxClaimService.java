package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.config.OutboxProperties;
import com.fowoco.server.reliability.domain.EventPublication;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {

    private final EventPublicationRepository repository;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxClaimService(
            EventPublicationRepository repository,
            OutboxProperties properties,
            OutboxMetrics metrics,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public List<UUID> claimBatch(String owner) {
        Instant now = clock.instant();
        List<EventPublication> candidates =
                repository.lockClaimable(now, properties.getBatchSize());
        List<UUID> claimed = new ArrayList<>(candidates.size());
        for (EventPublication publication : candidates) {
            publication.claim(owner, now, properties.getLeaseDuration());
            if (publication.attemptCount() > properties.getMaxAttempts()) {
                publication.requireReview(owner, "EVENT_ATTEMPTS_EXHAUSTED", now);
                metrics.recordReviewRequired();
            } else {
                claimed.add(publication.eventId());
            }
            repository.save(publication);
        }
        return List.copyOf(claimed);
    }
}
