package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.domain.EventPublication;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxReadService {

    private final EventPublicationRepository repository;

    public OutboxReadService(EventPublicationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public EventPublication requirePublication(UUID eventId) {
        return repository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
    }
}
