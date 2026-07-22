package com.fowoco.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthAuditEventTest {

    private static final UUID USER_ID = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TOKEN_FAMILY_ID =
            UUID.fromString("13000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void tokenFamilyEventContainsOnlySafeIdentifiers() {
        AuthAuditEvent event = AuthAuditEvent.tokenFamily(
                AuthAuditEvent.Action.REFRESH_REUSE_DETECTED,
                USER_ID,
                COMPANY_ID,
                TOKEN_FAMILY_ID,
                OCCURRED_AT
        );

        assertThat(event.action()).isEqualTo(AuthAuditEvent.Action.REFRESH_REUSE_DETECTED);
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.companyId()).isEqualTo(COMPANY_ID);
        assertThat(event.tokenFamilyId()).isEqualTo(TOKEN_FAMILY_ID);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void rejectedAnonymousAttemptDoesNotRequireAnEmailOrTokenValue() {
        AuthAuditEvent event = AuthAuditEvent.anonymous(
                AuthAuditEvent.Action.LOGIN_REJECTED,
                OCCURRED_AT
        );

        assertThat(event.userId()).isNull();
        assertThat(event.companyId()).isNull();
        assertThat(event.tokenFamilyId()).isNull();
    }

    @Test
    void tokenFamilyCannotBeRecordedWithoutItsAccountScope() {
        assertThatThrownBy(() -> AuthAuditEvent.tokenFamily(
                AuthAuditEvent.Action.TOKEN_FAMILY_REVOKED,
                null,
                null,
                TOKEN_FAMILY_ID,
                OCCURRED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authenticated account");
    }
}
