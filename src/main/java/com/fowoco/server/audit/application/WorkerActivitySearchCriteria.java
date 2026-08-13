package com.fowoco.server.audit.application;

import com.fowoco.server.audit.domain.AuditAction;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WorkerActivitySearchCriteria(
        UUID companyId,
        UUID workerId,
        Set<AuditAction> actions,
        Instant beforeCreatedAt,
        UUID beforeAuditEventId,
        int limit
) {
    public WorkerActivitySearchCriteria {
        actions = Set.copyOf(actions);
    }
}
