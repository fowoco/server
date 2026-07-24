package com.fowoco.server.reliability.application.port;

import com.fowoco.server.reliability.domain.EventPublication;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventPublicationRepository {

    EventPublication append(EventPublication publication);

    EventPublication save(EventPublication publication);

    List<EventPublication> lockClaimable(Instant now, int limit);

    Optional<EventPublication> findById(UUID eventId);

    Optional<EventPublication> findByIdForUpdate(UUID eventId);

    long countOutstanding();

    Optional<Instant> findOldestOutstandingOccurredAt();
}
