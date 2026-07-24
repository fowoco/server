package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.domain.EventPublicationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEventPublicationJpaRepository
        extends JpaRepository<EventPublicationJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT publication
            FROM EventPublicationJpaEntity publication
            WHERE (
                publication.status IN :readyStatuses
                AND publication.nextAttemptAt <= :now
            ) OR (
                publication.status = :processingStatus
                AND publication.leaseExpiresAt <= :now
            )
            ORDER BY publication.occurredAt ASC, publication.eventId ASC
            """)
    List<EventPublicationJpaEntity> lockClaimable(
            @Param("readyStatuses") Collection<EventPublicationStatus> readyStatuses,
            @Param("processingStatus") EventPublicationStatus processingStatus,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT publication
            FROM EventPublicationJpaEntity publication
            WHERE publication.eventId = :eventId
            """)
    Optional<EventPublicationJpaEntity> findByIdForUpdate(@Param("eventId") UUID eventId);

    long countByStatusIn(Collection<EventPublicationStatus> statuses);

    @Query("""
            SELECT MIN(publication.occurredAt)
            FROM EventPublicationJpaEntity publication
            WHERE publication.status IN :statuses
            """)
    Instant findOldestOccurredAt(
            @Param("statuses") Collection<EventPublicationStatus> statuses
    );
}
