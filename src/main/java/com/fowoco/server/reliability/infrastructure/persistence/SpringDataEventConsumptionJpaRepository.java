package com.fowoco.server.reliability.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEventConsumptionJpaRepository
        extends JpaRepository<EventConsumptionJpaEntity, UUID> {

    boolean existsByEventIdAndHandlerName(UUID eventId, String handlerName);
}
