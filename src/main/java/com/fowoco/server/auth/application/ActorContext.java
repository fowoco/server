package com.fowoco.server.auth.application;

import com.fowoco.server.auth.domain.UserRole;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticated actor identity used by application services.
 *
 * <p>The company identifier always comes from a validated access token, never from a request body.</p>
 */
public record ActorContext(
        UUID actorId,
        UUID companyId,
        Set<UserRole> roles
) {

    public ActorContext {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        roles = Set.copyOf(roles);
    }

    public boolean hasAnyRole(Set<UserRole> allowedRoles) {
        Objects.requireNonNull(allowedRoles, "allowedRoles must not be null");
        return roles.stream().anyMatch(allowedRoles::contains);
    }
}
