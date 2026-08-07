package com.fowoco.server.reliability.application.port;

import com.fowoco.server.reliability.domain.OutboxManualRetry;
import java.util.Optional;
import java.util.UUID;

public interface OutboxManualRetryRepository {

    Optional<OutboxManualRetry> findByEventIdAndCompanyIdAndKeyHash(
            UUID eventId,
            UUID companyId,
            String idempotencyKeyHash
    );

    OutboxManualRetry append(OutboxManualRetry retry);
}
