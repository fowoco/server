package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.OutboxManualRetryRepository;
import com.fowoco.server.reliability.domain.OutboxManualRetry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOutboxManualRetryRepository implements OutboxManualRetryRepository {

    private final SpringDataOutboxManualRetryJpaRepository repository;

    public JpaOutboxManualRetryRepository(SpringDataOutboxManualRetryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<OutboxManualRetry> findByEventIdAndCompanyIdAndKeyHash(
            UUID eventId,
            UUID companyId,
            String idempotencyKeyHash
    ) {
        return repository.findByEventIdAndCompanyIdAndIdempotencyKeyHash(
                        eventId,
                        companyId,
                        idempotencyKeyHash
                )
                .map(OutboxManualRetryJpaEntity::toDomain);
    }

    @Override
    public OutboxManualRetry append(OutboxManualRetry retry) {
        return repository.saveAndFlush(new OutboxManualRetryJpaEntity(retry)).toDomain();
    }
}
