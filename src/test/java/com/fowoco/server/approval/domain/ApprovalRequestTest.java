package com.fowoco.server.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.approval.application.error.ApprovalErrorCode;
import com.fowoco.server.common.error.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalRequestTest {

    private static final UUID APPROVAL_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final String FINGERPRINT = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    @Test
    void approvalIsBoundToTaskVersionContentRevisionAndFingerprint() {
        ApprovalRequest approval = pending();

        approval.approve(3, 2, FINGERPRINT, 4, ACTOR_ID, "확인", NOW.plusSeconds(1));

        assertThat(approval.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.isValidFor(2, FINGERPRINT)).isTrue();
        assertThat(approval.isValidFor(3, FINGERPRINT)).isFalse();
        assertThat(approval.isValidFor(2, "b".repeat(64))).isFalse();
    }

    @Test
    void changedRevisionCannotUsePendingApproval() {
        ApprovalRequest approval = pending();

        assertThatThrownBy(() -> approval.approve(
                3,
                3,
                FINGERPRINT,
                4,
                ACTOR_ID,
                null,
                NOW.plusSeconds(1)
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ApprovalErrorCode.APPROVAL_VERSION_MISMATCH)
                );
    }

    private ApprovalRequest pending() {
        return ApprovalRequest.create(
                APPROVAL_ID,
                TASK_ID,
                COMPANY_ID,
                3,
                2,
                FINGERPRINT,
                "{}",
                "{}",
                "[]",
                "{}",
                ACTOR_ID,
                NOW
        );
    }
}
