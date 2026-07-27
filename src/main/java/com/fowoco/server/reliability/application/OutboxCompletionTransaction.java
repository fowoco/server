package com.fowoco.server.reliability.application;

import com.fowoco.server.common.security.TenantDatabaseContext;
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
    private final TenantDatabaseContext tenantDatabaseContext;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxCompletionTransaction(
            EventPublicationRepository repository,
            TenantDatabaseContext tenantDatabaseContext,
            OutboxMetrics metrics,
            Clock clock
    ) {
        this.repository = repository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID eventId, UUID companyId, String owner) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
        Instant now = clock.instant();
        EventPublication publication = repository
                .findByIdAndCompanyIdForUpdate(eventId, companyId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
        publication.complete(owner, now);
        repository.save(publication);
        metrics.recordCompleted();
    }
}
