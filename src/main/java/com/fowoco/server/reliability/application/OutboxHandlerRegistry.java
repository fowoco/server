package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.port.DomainEventHandler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OutboxHandlerRegistry {

    private final List<DomainEventHandler> handlers;

    public OutboxHandlerRegistry(List<DomainEventHandler> handlers) {
        Set<String> names = new HashSet<>();
        handlers.forEach(handler -> {
            String name = handler.handlerName();
            if (name == null || name.isBlank() || name.length() > 120) {
                throw new IllegalArgumentException(
                        "Event handler name must be 1 to 120 characters."
                );
            }
            if (!names.add(name)) {
                throw new IllegalStateException("Duplicate event handler name: " + name);
            }
        });
        this.handlers = List.copyOf(handlers);
    }

    public List<DomainEventHandler> handlersFor(String eventType) {
        return handlers.stream()
                .filter(handler -> handler.supports(eventType))
                .toList();
    }
}
