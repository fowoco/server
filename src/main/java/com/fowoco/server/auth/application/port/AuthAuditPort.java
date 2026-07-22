package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.application.AuthAuditEvent;

/**
 * Receives privacy-safe authentication events. The append-only persistent implementation belongs
 * to the audit module; the Auth module must never pass credentials or raw tokens through this port.
 */
public interface AuthAuditPort {

    void record(AuthAuditEvent event);
}
