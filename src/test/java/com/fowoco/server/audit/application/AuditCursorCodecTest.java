package com.fowoco.server.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.approval.application.error.ApprovalErrorCode;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCursorCodecTest {

    private static final UUID EVENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-07-23T00:00:00Z");

    private final AuditCursorCodec codec = new AuditCursorCodec();

    @Test
    void cursorRoundTripPreservesKeyset() {
        String cursor = codec.encode(event());

        AuditCursorCodec.DecodedAuditCursor decoded = codec.decode(cursor);

        assertThat(decoded.createdAt()).isEqualTo(CREATED_AT);
        assertThat(decoded.auditEventId()).isEqualTo(EVENT_ID);
    }

    @Test
    void malformedCursorIsRejected() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ApprovalErrorCode.INVALID_AUDIT_CURSOR)
                );
    }

    private AuditEvent event() {
        return new AuditEvent(
                EVENT_ID,
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                ActorType.HR_USER,
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                UserRole.ADMIN,
                AuditAction.TASK_APPROVED,
                AuditTargetType.TASK,
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "request-1",
                null,
                "1",
                "업무 승인",
                CREATED_AT
        );
    }
}
