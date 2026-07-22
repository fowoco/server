package com.fowoco.server.auth.application;

import java.time.Instant;
import java.util.Objects;

public final class RefreshResult {

    private final String accessToken;
    private final Instant accessTokenExpiresAt;
    private final long accessTokenExpiresInSeconds;
    private final String refreshToken;
    private final Instant refreshTokenExpiresAt;

    public RefreshResult(
            String accessToken,
            Instant accessTokenExpiresAt,
            long accessTokenExpiresInSeconds,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
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
