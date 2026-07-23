package com.fowoco.server.audit.domain;

import com.fowoco.server.auth.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID auditEventId,
        UUID companyId,
        ActorType actorType,
        UUID actorId,
        UserRole userRole,
        AuditAction action,
        AuditTargetType targetType,
        UUID targetId,
        String requestId,
        String traceId,
        String eventVersion,
        String changeSummary,
        Instant createdAt
) {
}
