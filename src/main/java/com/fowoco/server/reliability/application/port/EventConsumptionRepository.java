package com.fowoco.server.reliability.application.port;

import com.fowoco.server.reliability.domain.EventConsumption;
import java.util.UUID;

public interface EventConsumptionRepository {

    boolean existsByEventIdAndHandlerName(UUID eventId, String handlerName);

    EventConsumption save(EventConsumption consumption);
}
