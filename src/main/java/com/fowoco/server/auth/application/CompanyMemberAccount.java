package com.fowoco.server.auth.application;

import com.fowoco.server.auth.domain.AccountStatus;
import com.fowoco.server.auth.domain.UserRole;
import java.util.Objects;
import java.util.UUID;

public record CompanyMemberAccount(
        UUID userId,
        String displayName,
        UserRole role,
        AccountStatus status
) {

    public CompanyMemberAccount {
        Objects.requireNonNull(userId, "userId must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        displayName = displayName.strip();
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean active() {
        return status == AccountStatus.ACTIVE;
    }
}
