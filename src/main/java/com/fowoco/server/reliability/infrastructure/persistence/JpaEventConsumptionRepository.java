package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.EventConsumptionRepository;
import com.fowoco.server.reliability.domain.EventConsumption;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEventConsumptionRepository implements EventConsumptionRepository {

    private final SpringDataEventConsumptionJpaRepository repository;

    public JpaEventConsumptionRepository(
            SpringDataEventConsumptionJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEventIdAndHandlerName(UUID eventId, String handlerName) {
        return repository.existsByEventIdAndHandlerName(eventId, handlerName);
    }

    @Override
    public EventConsumption save(EventConsumption consumption) {
        return repository.saveAndFlush(new EventConsumptionJpaEntity(consumption)).toDomain();
    }
}
