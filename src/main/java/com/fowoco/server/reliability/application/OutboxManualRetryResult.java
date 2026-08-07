package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.domain.EventPublicationStatus;
import java.time.Instant;
import java.util.UUID;

public record OutboxManualRetryResult(
        UUID eventId,
        EventPublicationStatus acceptedStatus,
        long acceptedVersion,
        Instant acceptedAt,
        boolean alreadyRequested
) {
}
