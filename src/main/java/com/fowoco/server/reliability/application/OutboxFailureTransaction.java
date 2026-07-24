package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.OutboxFailureClassifier.FailureClassification;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.config.OutboxProperties;
import com.fowoco.server.reliability.domain.EventPublication;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxFailureTransaction {

    private final EventPublicationRepository repository;
    private final OutboxFailureClassifier classifier;
    private final OutboxBackoffPolicy backoffPolicy;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxFailureTransaction(
            EventPublicationRepository repository,
            OutboxFailureClassifier classifier,
            OutboxBackoffPolicy backoffPolicy,
            OutboxProperties properties,
            OutboxMetrics metrics,
            Clock clock
    ) {
        this.repository = repository;
        this.classifier = classifier;
        this.backoffPolicy = backoffPolicy;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureOutcome recordFailure(
            UUID eventId,
            String owner,
            Throwable failure
    ) {
        Instant now = clock.instant();
        EventPublication publication = repository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
        FailureClassification classification = classifier.classify(failure);
        boolean exhausted = publication.attemptCount() >= properties.getMaxAttempts();
        if (!classification.retryable() || exhausted) {
            publication.requireReview(owner, classification.errorCode(), now);
            repository.save(publication);
            metrics.recordReviewRequired();
            return new FailureOutcome(classification.errorCode(), false);
        }
        publication.retry(
                owner,
                classification.errorCode(),
                now.plus(backoffPolicy.delayForAttempt(publication.attemptCount())),
                now
        );
        repository.save(publication);
        metrics.recordRetry();
        return new FailureOutcome(classification.errorCode(), true);
    }

    public record FailureOutcome(String errorCode, boolean retryScheduled) {
    }
}
