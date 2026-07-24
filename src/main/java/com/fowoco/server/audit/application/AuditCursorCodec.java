package com.fowoco.server.audit.application;

import com.fowoco.server.approval.application.error.ApprovalErrorCode;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuditCursorCodec {

    private static final String DELIMITER = "|";

    public String encode(AuditEvent event) {
        String raw = event.createdAt() + DELIMITER + event.auditEventId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public DecodedAuditCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new DecodedAuditCursor(null, null);
        }
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            return new DecodedAuditCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    private ApiException invalidCursor() {
        return new ApiException(ApprovalErrorCode.INVALID_AUDIT_CURSOR);
    }

    public record DecodedAuditCursor(Instant createdAt, UUID auditEventId) {
    }
}
