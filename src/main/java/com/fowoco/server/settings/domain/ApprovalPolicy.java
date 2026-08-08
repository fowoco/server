package com.fowoco.server.settings.domain;

import com.fowoco.server.auth.domain.UserRole;
import java.util.Objects;

public enum ApprovalPolicy {
    ADMIN_ONLY,
    ADMIN_OR_HR;

    public boolean permits(UserRole role) {
        Objects.requireNonNull(role, "role must not be null");
        return switch (this) {
            case ADMIN_ONLY -> role == UserRole.ADMIN;
            case ADMIN_OR_HR -> role == UserRole.ADMIN || role == UserRole.HR;
        };
    }
}
