package com.fowoco.server.auth.application;

import com.fowoco.server.auth.domain.UserRole;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SignupResult(
        UUID userId,
        UUID companyId,
        String companyName,
        String displayName,
        String email,
        UserRole role,
        Instant createdAt
) {

    public SignupResult {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(companyName, "companyName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
