package com.fowoco.server.audit.application;

import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditTargetType;
import java.time.Instant;
import java.util.UUID;

public record AuditSearchCriteria(
        UUID companyId,
        ActorType actorType,
        AuditAction action,
        AuditTargetType targetType,
        UUID targetId,
        String traceId,
        Instant createdFrom,
        Instant createdTo,
        Instant beforeCreatedAt,
        UUID beforeAuditEventId,
        int limit
) {
}
