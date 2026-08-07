package com.fowoco.server.reliability.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.reliability.application.OutboxManualRetryResult;
import com.fowoco.server.reliability.domain.EventPublicationStatus;
import java.time.Instant;
import java.util.UUID;

public record OutboxManualRetryResponse(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("accepted_status") EventPublicationStatus acceptedStatus,
        @JsonProperty("accepted_version") long acceptedVersion,
        @JsonProperty("accepted_at") Instant acceptedAt,
        @JsonProperty("already_requested") boolean alreadyRequested
) {
    static OutboxManualRetryResponse from(OutboxManualRetryResult result) {
        return new OutboxManualRetryResponse(
                result.eventId(),
                result.acceptedStatus(),
                result.acceptedVersion(),
                result.acceptedAt(),
                result.alreadyRequested()
        );
    }
}
