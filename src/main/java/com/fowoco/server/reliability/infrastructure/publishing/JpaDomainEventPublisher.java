package com.fowoco.server.reliability.infrastructure.publishing;

import com.fowoco.server.reliability.application.port.DomainEventPublisher;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventPublication;
import com.fowoco.server.reliability.infrastructure.serialization.EventPayloadCodec;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class JpaDomainEventPublisher implements DomainEventPublisher {

    private final EventPublicationRepository repository;
    private final EventPayloadCodec payloadCodec;
    private final Clock clock;

    public JpaDomainEventPublisher(
            EventPublicationRepository repository,
            EventPayloadCodec payloadCodec,
            Clock clock
    ) {
        this.repository = repository;
        this.payloadCodec = payloadCodec;
        this.clock = clock;
    }

    @Override
    public void publish(DomainEventEnvelope event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Durable domain events require an active transaction."
            );
        }
        Instant publishedAt = clock.instant();
        repository.append(EventPublication.pending(
                event,
                payloadCodec.encode(event.payload()),
                publishedAt
        ));
    }
}
