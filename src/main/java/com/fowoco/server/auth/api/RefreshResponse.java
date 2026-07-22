package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.application.RefreshResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
        name = "RefreshResponse",
        description = "Refresh Token 회전 성공 응답. 새 Refresh Token은 HttpOnly 쿠키로만 전달됩니다."
)
public final class RefreshResponse {

    @JsonProperty("access_token")
    @Schema(
            name = "access_token",
            description = "새로 발급한 JWT Access Token",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String accessToken;

    @JsonProperty("token_type")
    @Schema(
            name = "token_type",
            allowableValues = "Bearer",
            example = "Bearer",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String tokenType;

    @JsonProperty("expires_in_seconds")
    @Schema(
            name = "expires_in_seconds",
            description = "새 Access Token 만료까지 남은 초",
            example = "900",
            minimum = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final long expiresInSeconds;

    @JsonProperty("expires_at")
    @Schema(
            name = "expires_at",
            description = "새 Access Token 만료 시각(UTC)",
            format = "date-time",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final Instant expiresAt;

    private RefreshResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            Instant expiresAt
    ) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
        this.expiresAt = expiresAt;
    }

    public static RefreshResponse from(RefreshResult result) {
        return new RefreshResponse(
                result.accessToken(),
                "Bearer",
                result.accessTokenExpiresInSeconds(),
                result.accessTokenExpiresAt()
        );
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
