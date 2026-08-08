package com.fowoco.server.settings.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(
        name = "MinimalCompanyMemberResponse",
        description = "VIEWER에 제공하는 최소 구성원 projection"
)
public record MinimalCompanyMemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
        @Schema(maxLength = 80, requiredMode = Schema.RequiredMode.REQUIRED) String displayName
) implements CompanyMemberItemResponse {
}
