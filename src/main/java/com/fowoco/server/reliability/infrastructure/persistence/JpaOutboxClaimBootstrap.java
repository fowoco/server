package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.application.port.OutboxClaimBootstrap;
import com.fowoco.server.reliability.domain.EventPublication;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * H2/local claim adapter preserving the domain claim behavior without PostgreSQL functions.
 */
@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "transaction-only",
        matchIfMissing = true
)
public class JpaOutboxClaimBootstrap implements OutboxClaimBootstrap {

    private static final String ATTEMPTS_EXHAUSTED = "EVENT_ATTEMPTS_EXHAUSTED";

    private final EventPublicationRepository repository;

    public JpaOutboxClaimBootstrap(EventPublicationRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ClaimResult> claim(
            String owner,
            Instant now,
            Duration leaseDuration,
            int batchSize,
            int maxAttempts
    ) {
        List<EventPublication> candidates = repository.lockClaimable(now, batchSize);
        List<ClaimResult> results = new ArrayList<>(candidates.size());
        for (EventPublication publication : candidates) {
            publication.claim(owner, now, leaseDuration);
            boolean reviewRequired = publication.attemptCount() > maxAttempts;
            if (reviewRequired) {
                publication.requireReview(owner, ATTEMPTS_EXHAUSTED, now);
            }
            repository.save(publication);
            results.add(new ClaimResult(
                    publication.eventId(),
                    publication.companyId(),
                    reviewRequired
            ));
        }
        return List.copyOf(results);
    }
}
