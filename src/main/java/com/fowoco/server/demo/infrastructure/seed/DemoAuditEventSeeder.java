package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.AuditSeed;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class DemoAuditEventSeeder {

    private static final String EVENT_VERSION = "1";

    private final AuditEventRepository auditEventRepository;

    DemoAuditEventSeeder(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = Objects.requireNonNull(
                auditEventRepository,
                "auditEventRepository must not be null"
        );
    }

    void seed(AuditSeed seed, DemoOperationalSeedContext context) {
        Optional<AuditEvent> existing = findExisting(seed);
        if (existing.isPresent()) {
            verifyExisting(existing.orElseThrow(), seed, context);
            return;
        }
        auditEventRepository.append(new AuditEvent(
                seed.auditEventId(),
                context.companyId(),
                seed.actorType(),
                actorId(seed, context),
                seed.userRole(),
                seed.action(),
                seed.targetType(),
                seed.targetId(),
                seed.requestId(),
                seed.traceId(),
                EVENT_VERSION,
                seed.changeSummary(),
                context.now().minus(seed.hoursAgo(), ChronoUnit.HOURS)
        ));
    }

    Optional<AuditEvent> findExisting(AuditSeed seed) {
        return auditEventRepository.findById(seed.auditEventId());
    }

    void verifyExisting(
            AuditEvent event,
            AuditSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!context.companyId().equals(event.companyId())
                || event.actorType() != seed.actorType()
                || !Objects.equals(actorId(seed, context), event.actorId())
                || event.userRole() != seed.userRole()
                || event.action() != seed.action()
                || event.targetType() != seed.targetType()
                || !seed.targetId().equals(event.targetId())
                || !seed.requestId().equals(event.requestId())
                || !seed.traceId().equals(event.traceId())
                || !EVENT_VERSION.equals(event.eventVersion())
                || !seed.changeSummary().equals(event.changeSummary())) {
            throw new IllegalStateException(
                    "a reserved demo audit event id already belongs to different audit data"
            );
        }
    }

    private UUID actorId(AuditSeed seed, DemoOperationalSeedContext context) {
        return seed.actorType() == ActorType.HR_USER ? context.actorId() : null;
    }
}
