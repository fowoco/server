package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.application.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        name = "LoginResponse",
        description = "로그인 성공 응답. Refresh Token은 본문이 아니라 HttpOnly 쿠키로 전달됩니다."
)
public final class LoginResponse {

    @JsonProperty("user_id")
    @Schema(
            name = "user_id",
            description = "로그인한 사용자 ID",
            format = "uuid",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final UUID userId;
    @JsonProperty("company_id")
    @Schema(
            name = "company_id",
            description = "JWT로 고정되는 사업장 ID",
            format = "uuid",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final UUID companyId;
    @JsonProperty("company_name")
    @Schema(
            name = "company_name",
            description = "화면 표시용 사업장 이름",
            example = "포우코 제조",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String companyName;
    @Schema(
            description = "사용자 역할",
            allowableValues = {"ADMIN", "HR", "VIEWER"},
            example = "HR",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String role;
    @JsonProperty("access_token")
    @Schema(
            name = "access_token",
            description = "보호 API의 Authorization 헤더에 넣는 JWT Access Token",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.<payload>.<signature>",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String accessToken;
    @JsonProperty("token_type")
    @Schema(
            name = "token_type",
            description = "Authorization 헤더의 인증 방식",
            allowableValues = "Bearer",
            example = "Bearer",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String tokenType;
    @JsonProperty("expires_in_seconds")
    @Schema(
            name = "expires_in_seconds",
            description = "Access Token 만료까지 남은 초",
            example = "900",
            minimum = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final long expiresInSeconds;
    @JsonProperty("expires_at")
    @Schema(
            name = "expires_at",
            description = "Access Token 만료 시각(UTC)",
            example = "2026-07-22T01:15:00Z",
            format = "date-time",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
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
