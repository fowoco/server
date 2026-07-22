package com.fowoco.server.auth.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuthAuditEvent {

    public enum Action {
        LOGIN_SUCCEEDED,
        LOGIN_REJECTED,
        REFRESH_SUCCEEDED,
        REFRESH_REJECTED,
        REFRESH_REUSE_DETECTED,
        TOKEN_FAMILY_REVOKED,
        LOGOUT_COMPLETED
    }

    private final Action action;
    private final UUID userId;
    private final UUID companyId;
    private final UUID tokenFamilyId;
    private final Instant occurredAt;

    private AuthAuditEvent(
            Action action,
            UUID userId,
            UUID companyId,
            UUID tokenFamilyId,
            Instant occurredAt
    ) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        if ((userId == null) != (companyId == null)) {
            throw new IllegalArgumentException("userId and companyId must be present together");
        }
        if (tokenFamilyId != null && userId == null) {
            throw new IllegalArgumentException("tokenFamilyId requires an authenticated account");
        }
        this.userId = userId;
        this.companyId = companyId;
        this.tokenFamilyId = tokenFamilyId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AuthAuditEvent anonymous(Action action, Instant occurredAt) {
        return new AuthAuditEvent(action, null, null, null, occurredAt);
    }

    public static AuthAuditEvent account(
            Action action,
            UUID userId,
            UUID companyId,
            Instant occurredAt
    ) {
        return new AuthAuditEvent(action, userId, companyId, null, occurredAt);
    }

    public static AuthAuditEvent tokenFamily(
            Action action,
            UUID userId,
            UUID companyId,
            UUID tokenFamilyId,
            Instant occurredAt
    ) {
        return new AuthAuditEvent(action, userId, companyId, tokenFamilyId, occurredAt);
    }

    public Action action() {
        return action;
    }

    public UUID userId() {
        return userId;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID tokenFamilyId() {
        return tokenFamilyId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
