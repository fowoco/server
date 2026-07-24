package com.fowoco.server.audit.infrastructure.persistence;

import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditTargetType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, UUID> {

    List<AuditEventJpaEntity> findTop200ByCompanyIdAndTargetTypeAndTargetIdOrderByCreatedAtAscAuditEventIdAsc(
            UUID companyId,
            AuditTargetType targetType,
            UUID targetId
    );

    @Query("""
            SELECT event
              FROM AuditEventJpaEntity event
             WHERE event.companyId = :companyId
               AND (:actorType IS NULL OR event.actorType = :actorType)
               AND (:action IS NULL OR event.action = :action)
               AND (:targetType IS NULL OR event.targetType = :targetType)
               AND (:targetId IS NULL OR event.targetId = :targetId)
               AND (:traceId IS NULL OR event.traceId = :traceId)
               AND (:createdFrom IS NULL OR event.createdAt >= :createdFrom)
               AND (:createdTo IS NULL OR event.createdAt <= :createdTo)
               AND (
                    :beforeCreatedAt IS NULL
                    OR event.createdAt < :beforeCreatedAt
                    OR (
                        event.createdAt = :beforeCreatedAt
                        AND event.auditEventId < :beforeAuditEventId
                    )
               )
             ORDER BY event.createdAt DESC, event.auditEventId DESC
            """)
    List<AuditEventJpaEntity> search(
            @Param("companyId") UUID companyId,
            @Param("actorType") ActorType actorType,
            @Param("action") AuditAction action,
            @Param("targetType") AuditTargetType targetType,
            @Param("targetId") UUID targetId,
            @Param("traceId") String traceId,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeAuditEventId") UUID beforeAuditEventId,
            Pageable pageable
    );
}
