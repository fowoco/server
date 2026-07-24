package com.fowoco.server.auth.application;

import com.fowoco.server.auth.domain.UserRole;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class LoginResult {

    private final UUID userId;
    private final UUID companyId;
    private final String companyName;
    private final String displayName;
    private final UserRole role;
    private final String accessToken;
    private final Instant accessTokenExpiresAt;
    private final long accessTokenExpiresInSeconds;
    private final String refreshToken;
    private final Instant refreshTokenExpiresAt;

    public LoginResult(
            UUID userId,
            UUID companyId,
            String companyName,
            String displayName,
            UserRole role,
            String accessToken,
            Instant accessTokenExpiresAt,
            long accessTokenExpiresInSeconds,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.companyName = requireText(companyName, "companyName");
        this.displayName = requireText(displayName, "displayName");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.accessToken = requireText(accessToken, "accessToken");
        this.accessTokenExpiresAt = Objects.requireNonNull(
                accessTokenExpiresAt,
                "accessTokenExpiresAt must not be null"
        );
        if (accessTokenExpiresInSeconds <= 0) {
            throw new IllegalArgumentException("accessTokenExpiresInSeconds must be positive");
        }
        this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
        this.refreshToken = requireText(refreshToken, "refreshToken");
        this.refreshTokenExpiresAt = Objects.requireNonNull(
                refreshTokenExpiresAt,
                "refreshTokenExpiresAt must not be null"
        );
    }

    public UUID userId() {
        return userId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String companyName() {
        return companyName;
    }

    public String displayName() {
        return displayName;
    }

    public UserRole role() {
        return role;
    }

    public String accessToken() {
        return accessToken;
    }

    public Instant accessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public long accessTokenExpiresInSeconds() {
        return accessTokenExpiresInSeconds;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public Instant refreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
