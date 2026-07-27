package com.fowoco.server.reliability.application;

import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.application.port.OutboxTimeSource;
import com.fowoco.server.reliability.domain.EventPublication;
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
    private final OutboxTimeSource timeSource;

    public OutboxCompletionTransaction(
            EventPublicationRepository repository,
            TenantDatabaseContext tenantDatabaseContext,
            OutboxMetrics metrics,
            OutboxTimeSource timeSource
    ) {
        this.repository = repository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.metrics = metrics;
        this.timeSource = timeSource;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID eventId, UUID companyId, String owner) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
        EventPublication publication = repository
                .findByIdAndCompanyIdForUpdate(eventId, companyId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
        Instant now = timeSource.now();
        publication.complete(owner, now);
        repository.save(publication);
        metrics.recordCompleted();
    }
}
