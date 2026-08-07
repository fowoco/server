package com.fowoco.server.reliability.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOutboxManualRetryJpaRepository
        extends JpaRepository<OutboxManualRetryJpaEntity, UUID> {

    Optional<OutboxManualRetryJpaEntity> findByEventIdAndCompanyIdAndIdempotencyKeyHash(
            UUID eventId,
            UUID companyId,
            String idempotencyKeyHash
    );
}
