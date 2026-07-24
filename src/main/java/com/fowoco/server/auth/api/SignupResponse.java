package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.application.SignupResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
        name = "SignupResponse",
        description = "사업장과 최초 ADMIN 계정 생성 결과. Token은 발급하지 않습니다."
)
public final class SignupResponse {

    @JsonProperty("user_id")
    @Schema(name = "user_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID userId;
    @JsonProperty("company_id")
    @Schema(name = "company_id", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private final UUID companyId;
    @JsonProperty("company_name")
    @Schema(name = "company_name", example = "한빛정밀", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String companyName;
    @JsonProperty("display_name")
    @Schema(name = "display_name", example = "김경민", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String displayName;
    @Schema(
            description = "등록된 로그인 이메일",
            example = "name@company.com",
            format = "email",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String email;
    @Schema(
            description = "최초 계정 역할. Client 입력이 아니라 Server가 결정합니다.",
            allowableValues = "ADMIN",
            example = "ADMIN",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private final String role;
    @JsonProperty("created_at")
    @Schema(name = "created_at", format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Instant createdAt;

    private SignupResponse(
            UUID userId,
            UUID companyId,
            String companyName,
            String displayName,
            String email,
            String role,
            Instant createdAt
    ) {
        this.userId = userId;
        this.companyId = companyId;
        this.companyName = companyName;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(
                result.userId(),
                result.companyId(),
                result.companyName(),
                result.displayName(),
                result.email(),
                result.role().name(),
                result.createdAt()
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

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
