package com.fowoco.server.audit.infrastructure.persistence;

import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
class AuditEventJpaEntity {

    @Id
    @Column(name = "audit_event_id", nullable = false, updatable = false)
    private UUID auditEventId;
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30, updatable = false)
    private ActorType actorType;
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", length = 20, updatable = false)
    private UserRole userRole;
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private AuditAction action;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40, updatable = false)
    private AuditTargetType targetType;
    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;
    @Column(name = "request_id", nullable = false, length = 128, updatable = false)
    private String requestId;
    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;
    @Column(name = "event_version", nullable = false, length = 30, updatable = false)
    private String eventVersion;
    @Column(name = "change_summary", nullable = false, length = 500, updatable = false)
    private String changeSummary;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEventJpaEntity() {
    }

    AuditEventJpaEntity(AuditEvent event) {
        this.auditEventId = event.auditEventId();
        this.companyId = event.companyId();
        this.actorType = event.actorType();
        this.actorId = event.actorId();
        this.userRole = event.userRole();
        this.action = event.action();
        this.targetType = event.targetType();
        this.targetId = event.targetId();
        this.requestId = event.requestId();
        this.traceId = event.traceId();
        this.eventVersion = event.eventVersion();
        this.changeSummary = event.changeSummary();
        this.createdAt = event.createdAt();
    }

    AuditEvent toDomain() {
        return new AuditEvent(
                auditEventId,
                companyId,
                actorType,
                actorId,
                userRole,
                action,
                targetType,
                targetId,
                requestId,
                traceId,
                eventVersion,
                changeSummary,
                createdAt
        );
    }
}
