package com.fowoco.server.audit.infrastructure.persistence;

import com.fowoco.server.audit.domain.AuditTargetType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, UUID> {

    List<AuditEventJpaEntity> findTop200ByCompanyIdAndTargetTypeAndTargetIdOrderByCreatedAtAscAuditEventIdAsc(
            UUID companyId,
            AuditTargetType targetType,
            UUID targetId
    );
}
