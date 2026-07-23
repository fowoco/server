package com.fowoco.server.audit.api;

import com.fowoco.server.audit.application.AuditEventView;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
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

    public static AuditEventResponse from(AuditEventView view) {
        return new AuditEventResponse(
                view.auditEventId(),
                view.actorType(),
                view.actorId(),
                view.userRole(),
                view.action(),
                view.targetType(),
                view.targetId(),
                view.requestId(),
                view.traceId(),
                view.eventVersion(),
                view.changeSummary(),
                view.createdAt()
        );
    }
}
