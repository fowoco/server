package com.fowoco.server.reliability.application;

import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.domain.EventPublication;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxReadService {

    private final EventPublicationRepository repository;
    private final TenantDatabaseContext tenantDatabaseContext;

    public OutboxReadService(
            EventPublicationRepository repository,
            TenantDatabaseContext tenantDatabaseContext
    ) {
        this.repository = repository;
        this.tenantDatabaseContext = tenantDatabaseContext;
    }

    @Transactional(readOnly = true)
    public EventPublication requirePublication(UUID eventId, UUID companyId) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
        return repository.findByIdAndCompanyId(eventId, companyId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
    }
}
