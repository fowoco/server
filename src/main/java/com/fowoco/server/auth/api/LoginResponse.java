package com.fowoco.server.auth.api;

import com.fowoco.server.auth.application.LoginResult;
import java.time.Instant;
import java.util.UUID;

public final class LoginResponse {

    private final UUID userId;
    private final UUID companyId;
    private final String companyName;
    private final String role;
    private final String accessToken;
    private final String tokenType;
    private final long expiresInSeconds;
    private final Instant expiresAt;

    private LoginResponse(
            UUID userId,
            UUID companyId,
            String companyName,
            String role,
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            Instant expiresAt
    ) {
        this.userId = userId;
        this.companyId = companyId;
        this.companyName = companyName;
        this.role = role;
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
        this.expiresAt = expiresAt;
    }

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.userId(),
                result.companyId(),
                result.companyName(),
                result.role().name(),
                result.accessToken(),
                "Bearer",
                result.accessTokenExpiresInSeconds(),
                result.accessTokenExpiresAt()
        );
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getRole() {
        return role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
