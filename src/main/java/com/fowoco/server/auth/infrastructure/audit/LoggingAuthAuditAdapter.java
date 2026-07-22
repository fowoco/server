package com.fowoco.server.auth.infrastructure.audit;

import com.fowoco.server.auth.application.AuthAuditEvent;
import com.fowoco.server.auth.application.port.AuthAuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Diagnostic adapter until the append-only audit store is connected by the audit module. */
@Component
public final class LoggingAuthAuditAdapter implements AuthAuditPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAuthAuditAdapter.class);

    @Override
    public void record(AuthAuditEvent event) {
        LOGGER.info(
                "auth_security_event action={} user_id={} company_id={} token_family_id={} occurred_at={}",
                event.action(),
                event.userId(),
                event.companyId(),
                event.tokenFamilyId(),
                event.occurredAt()
        );
    }
}
