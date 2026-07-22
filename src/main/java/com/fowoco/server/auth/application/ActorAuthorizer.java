package com.fowoco.server.auth.application;

import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies role and company isolation rules inside the application layer.
 */
@Component
public final class ActorAuthorizer {

    private static final Set<UserRole> HR_WRITE_ROLES = Set.of(UserRole.ADMIN, UserRole.HR);

    public void requireHrWrite(ActorContext actorContext) {
        requireAnyRole(actorContext, HR_WRITE_ROLES);
    }

    public void requireAnyRole(ActorContext actorContext, UserRole firstRole, UserRole... additionalRoles) {
        Objects.requireNonNull(firstRole, "firstRole must not be null");
        Objects.requireNonNull(additionalRoles, "additionalRoles must not be null");
        EnumSet<UserRole> allowedRoles = EnumSet.of(firstRole);
        Arrays.stream(additionalRoles)
                .map(role -> Objects.requireNonNull(role, "allowed role must not be null"))
                .forEach(allowedRoles::add);
        requireAnyRole(actorContext, allowedRoles);
    }

    public void requireSameCompany(ActorContext actorContext, UUID resourceCompanyId) {
        Objects.requireNonNull(actorContext, "actorContext must not be null");
        Objects.requireNonNull(resourceCompanyId, "resourceCompanyId must not be null");
        if (!actorContext.companyId().equals(resourceCompanyId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireAnyRole(ActorContext actorContext, Set<UserRole> allowedRoles) {
        Objects.requireNonNull(actorContext, "actorContext must not be null");
        if (!actorContext.hasAnyRole(allowedRoles)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
