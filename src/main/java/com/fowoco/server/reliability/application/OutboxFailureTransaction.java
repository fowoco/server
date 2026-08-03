package com.fowoco.server.reliability.application;

import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.reliability.application.OutboxFailureClassifier.FailureClassification;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.application.port.OutboxTimeSource;
import com.fowoco.server.reliability.config.OutboxProperties;
import com.fowoco.server.reliability.domain.EventPublication;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxFailureTransaction {

    private final EventPublicationRepository repository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final OutboxFailureClassifier classifier;
    private final OutboxBackoffPolicy backoffPolicy;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final OutboxTimeSource timeSource;

    public OutboxFailureTransaction(
            EventPublicationRepository repository,
            TenantDatabaseContext tenantDatabaseContext,
            OutboxFailureClassifier classifier,
            OutboxBackoffPolicy backoffPolicy,
            OutboxProperties properties,
            OutboxMetrics metrics,
            OutboxTimeSource timeSource
    ) {
        this.repository = repository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.classifier = classifier;
        this.backoffPolicy = backoffPolicy;
        this.properties = properties;
        this.metrics = metrics;
        this.timeSource = timeSource;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FailureOutcome recordFailure(
            UUID eventId,
            UUID companyId,
            String owner,
            Throwable failure
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
        EventPublication publication = repository
                .findByIdAndCompanyIdForUpdate(eventId, companyId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
        Instant now = timeSource.now();
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
