package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.AuditSeed;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

final class DemoAuditEventSeeder {

    private static final String TRACE_ID = "demo-seed-task-timeline";
    private static final String EVENT_VERSION = "1";

    private final AuditEventRepository auditEventRepository;

    DemoAuditEventSeeder(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = Objects.requireNonNull(
                auditEventRepository,
                "auditEventRepository must not be null"
        );
    }

    void seed(AuditSeed seed, DemoOperationalSeedContext context) {
        Optional<AuditEvent> existing = findExisting(seed, context);
        if (existing.isPresent()) {
            verifyExisting(existing.orElseThrow(), seed, context);
            return;
        }
        auditEventRepository.append(new AuditEvent(
                seed.auditEventId(),
                context.companyId(),
                ActorType.HR_USER,
                context.actorId(),
                UserRole.ADMIN,
                seed.action(),
                AuditTargetType.TASK,
                DemoOperationalSeedCatalog.TIMELINE_TASK_ID,
                seed.requestId(),
                TRACE_ID,
                EVENT_VERSION,
                seed.changeSummary(),
                context.now().minus(seed.hoursAgo(), ChronoUnit.HOURS)
        ));
    }

    Optional<AuditEvent> findExisting(AuditSeed seed, DemoOperationalSeedContext context) {
        return auditEventRepository
                .findTaskActivities(context.companyId(), DemoOperationalSeedCatalog.TIMELINE_TASK_ID)
                .stream()
                .filter(event -> seed.auditEventId().equals(event.auditEventId()))
                .findFirst();
    }

    void verifyExisting(
            AuditEvent event,
            AuditSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!context.companyId().equals(event.companyId())
                || event.actorType() != ActorType.HR_USER
                || !context.actorId().equals(event.actorId())
                || event.userRole() != UserRole.ADMIN
                || event.action() != seed.action()
                || event.targetType() != AuditTargetType.TASK
                || !DemoOperationalSeedCatalog.TIMELINE_TASK_ID.equals(event.targetId())
                || !seed.requestId().equals(event.requestId())
                || !seed.changeSummary().equals(event.changeSummary())) {
            throw new IllegalStateException(
                    "a reserved demo audit event id already belongs to different audit data"
            );
        }
    }
}
