package com.fowoco.server.reliability.application.port;

import com.fowoco.server.reliability.domain.DomainEventEnvelope;

@FunctionalInterface
public interface DomainEventPublisher {

    void publish(DomainEventEnvelope event);
}
