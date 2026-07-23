package com.fowoco.server.audit.application;

import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record AuditEventView(
        UUID auditEventId,
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

    public static AuditEventView from(AuditEvent event) {
        return new AuditEventView(
                event.auditEventId(),
                event.actorType(),
                event.actorId(),
                event.userRole(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.requestId(),
                event.traceId(),
                event.eventVersion(),
                event.changeSummary(),
                event.createdAt()
        );
    }
}
