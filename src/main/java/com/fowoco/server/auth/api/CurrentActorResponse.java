package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.application.ActorContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "CurrentActorResponse", description = "검증된 Access Token에서 확인한 현재 사용자 Context")
public record CurrentActorResponse(
        @JsonProperty("user_id")
        @Schema(name = "user_id", description = "현재 사용자 ID", format = "uuid")
        UUID userId,
        @JsonProperty("company_id")
        @Schema(name = "company_id", description = "현재 요청에 고정된 사업장 ID", format = "uuid")
        UUID companyId,
        @Schema(description = "현재 사용자의 역할", example = "[\"HR\"]")
        List<String> roles
) {

    public static CurrentActorResponse from(ActorContext actorContext) {
        List<String> roles = actorContext.roles().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        return new CurrentActorResponse(actorContext.actorId(), actorContext.companyId(), roles);
    }
}
