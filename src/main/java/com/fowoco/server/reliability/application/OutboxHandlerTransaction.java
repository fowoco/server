package com.fowoco.server.reliability.application;

import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.application.port.EventConsumptionRepository;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.application.port.OutboxTimeSource;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventConsumption;
import com.fowoco.server.reliability.domain.EventPublication;
import com.fowoco.server.reliability.infrastructure.serialization.EventPayloadCodec;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxHandlerTransaction {

    private final EventPublicationRepository publicationRepository;
    private final EventConsumptionRepository consumptionRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final EventPayloadCodec payloadCodec;
    private final UuidGenerator uuidGenerator;
    private final OutboxTimeSource timeSource;

    public OutboxHandlerTransaction(
            EventPublicationRepository publicationRepository,
            EventConsumptionRepository consumptionRepository,
            TenantDatabaseContext tenantDatabaseContext,
            EventPayloadCodec payloadCodec,
            UuidGenerator uuidGenerator,
            OutboxTimeSource timeSource
    ) {
        this.publicationRepository = publicationRepository;
        this.consumptionRepository = consumptionRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.payloadCodec = payloadCodec;
        this.uuidGenerator = uuidGenerator;
        this.timeSource = timeSource;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(
            UUID eventId,
            UUID companyId,
            String owner,
            DomainEventHandler handler
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
        EventPublication publication = publicationRepository
                .findByIdAndCompanyIdForUpdate(eventId, companyId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
        Instant now = timeSource.now();
        publication.requireActiveLease(owner, now);
        String handlerName = handler.handlerName();
        if (consumptionRepository.existsByEventIdAndHandlerName(eventId, handlerName)) {
            return false;
        }
        DomainEventEnvelope event = publication.toEnvelope(
                payloadCodec.decode(publication.payloadJson())
        );
        handler.handle(event);
        consumptionRepository.save(new EventConsumption(
                uuidGenerator.generate(),
                eventId,
                publication.companyId(),
                handlerName,
                now
        ));
        return true;
    }
}
