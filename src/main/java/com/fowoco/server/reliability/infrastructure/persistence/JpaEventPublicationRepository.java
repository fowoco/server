package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.domain.EventPublication;
import com.fowoco.server.reliability.domain.EventPublicationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEventPublicationRepository implements EventPublicationRepository {

    private static final List<EventPublicationStatus> READY_STATUSES = List.of(
            EventPublicationStatus.PENDING,
            EventPublicationStatus.RETRY_WAIT
    );
    private static final List<EventPublicationStatus> OUTSTANDING_STATUSES = List.of(
            EventPublicationStatus.PENDING,
            EventPublicationStatus.PROCESSING,
            EventPublicationStatus.RETRY_WAIT,
            EventPublicationStatus.REVIEW_REQUIRED
    );

    private final SpringDataEventPublicationJpaRepository repository;

    public JpaEventPublicationRepository(
            SpringDataEventPublicationJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public EventPublication append(EventPublication publication) {
        return repository.saveAndFlush(new EventPublicationJpaEntity(publication)).toDomain();
    }

    @Override
    public EventPublication save(EventPublication publication) {
        EventPublicationJpaEntity entity = repository.findById(publication.eventId())
                .orElseThrow(() -> new IllegalStateException("Event publication not found."));
        entity.apply(publication);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public List<EventPublication> lockClaimable(Instant now, int limit) {
        return repository.lockClaimable(
                        READY_STATUSES,
                        EventPublicationStatus.PROCESSING,
                        now,
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(EventPublicationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<EventPublication> findById(UUID eventId) {
        return repository.findById(eventId)
                .map(EventPublicationJpaEntity::toDomain);
    }

    @Override
    public Optional<EventPublication> findByIdForUpdate(UUID eventId) {
        return repository.findByIdForUpdate(eventId)
                .map(EventPublicationJpaEntity::toDomain);
    }

    @Override
    public long countOutstanding() {
        return repository.countByStatusIn(OUTSTANDING_STATUSES);
    }

    @Override
    public Optional<Instant> findOldestOutstandingOccurredAt() {
        return Optional.ofNullable(repository.findOldestOccurredAt(OUTSTANDING_STATUSES));
    }
}
