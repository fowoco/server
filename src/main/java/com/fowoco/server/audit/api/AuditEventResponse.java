package com.fowoco.server.audit.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.audit.application.AuditEventView;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        @JsonProperty("audit_event_id") UUID auditEventId,
        @JsonProperty("actor_type") ActorType actorType,
        @JsonProperty("actor_id") UUID actorId,
        @JsonProperty("user_role") UserRole userRole,
        AuditAction action,
        @JsonProperty("target_type") AuditTargetType targetType,
        @JsonProperty("target_id") UUID targetId,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("event_version") String eventVersion,
        @JsonProperty("change_summary") String changeSummary,
        @JsonProperty("created_at") Instant createdAt
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
