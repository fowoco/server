package com.fowoco.server.reliability.application.port;

import com.fowoco.server.reliability.domain.DomainEventEnvelope;

public interface DomainEventHandler {

    String handlerName();

    boolean supports(String eventType);

    void handle(DomainEventEnvelope event);
}
