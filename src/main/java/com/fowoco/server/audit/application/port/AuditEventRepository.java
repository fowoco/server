package com.fowoco.server.audit.application.port;

import com.fowoco.server.audit.application.AuditSearchCriteria;
import com.fowoco.server.audit.domain.AuditEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository {

    void append(AuditEvent event);

    Optional<AuditEvent> findById(UUID auditEventId);

    List<AuditEvent> findTaskActivities(UUID companyId, UUID taskId);

    List<AuditEvent> search(AuditSearchCriteria criteria);
}
