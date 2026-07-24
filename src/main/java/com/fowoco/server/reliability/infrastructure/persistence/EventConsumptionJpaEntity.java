package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.domain.EventConsumption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_consumption")
class EventConsumptionJpaEntity {

    @Id
    @Column(name = "consumption_id", nullable = false, updatable = false)
    private UUID consumptionId;
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "handler_name", nullable = false, length = 120, updatable = false)
    private String handlerName;
    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected EventConsumptionJpaEntity() {
    }

    EventConsumptionJpaEntity(EventConsumption consumption) {
        consumptionId = consumption.consumptionId();
        eventId = consumption.eventId();
        companyId = consumption.companyId();
        handlerName = consumption.handlerName();
        completedAt = consumption.completedAt();
    }

    EventConsumption toDomain() {
        return new EventConsumption(
                consumptionId,
                eventId,
                companyId,
                handlerName,
                completedAt
        );
    }
}
