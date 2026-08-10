package com.fowoco.server.settings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.auth.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(
        name = "DetailedCompanyMemberResponse",
        description = "ADMIN과 HR에 제공하는 구성원 projection"
)
public record DetailedCompanyMemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(maxLength = 80, requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UserRole> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean approvalPermission
) implements CompanyMemberItemResponse {

    public DetailedCompanyMemberResponse {
        roles = List.copyOf(roles);
    }
}
