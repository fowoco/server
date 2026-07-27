package com.fowoco.server.reliability.application;

import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.application.port.EventConsumptionRepository;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventConsumption;
import com.fowoco.server.reliability.domain.EventPublication;
import com.fowoco.server.reliability.infrastructure.serialization.EventPayloadCodec;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxHandlerTransaction {

    private final EventPublicationRepository publicationRepository;
    private final EventConsumptionRepository consumptionRepository;
    private final EventPayloadCodec payloadCodec;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public OutboxHandlerTransaction(
            EventPublicationRepository publicationRepository,
            EventConsumptionRepository consumptionRepository,
            EventPayloadCodec payloadCodec,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.publicationRepository = publicationRepository;
        this.consumptionRepository = consumptionRepository;
        this.payloadCodec = payloadCodec;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(
            UUID eventId,
            String owner,
            DomainEventHandler handler
    ) {
        Instant now = clock.instant();
        EventPublication publication = publicationRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
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
