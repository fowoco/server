package com.fowoco.server.auth.api;

import com.fowoco.server.auth.application.ActorContext;
import java.util.List;
import java.util.UUID;

public record CurrentActorResponse(
        UUID userId,
        UUID companyId,
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
