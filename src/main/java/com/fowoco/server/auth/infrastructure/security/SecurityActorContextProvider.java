package com.fowoco.server.auth.infrastructure.security;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public final class SecurityActorContextProvider implements ActorContextProvider {

    @Override
    public ActorContext requireCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw authenticationRequired();
        }

        try {
            UUID actorId = UUID.fromString(jwtAuthentication.getToken().getSubject());
            UUID companyId = UUID.fromString(
                    jwtAuthentication.getToken().getClaimAsString("company_id")
            );
            List<String> roleClaims = jwtAuthentication.getToken().getClaimAsStringList("roles");
            if (roleClaims == null || roleClaims.isEmpty()) {
                throw authenticationRequired();
            }
            Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
            roleClaims.stream().map(UserRole::valueOf).forEach(roles::add);
            return new ActorContext(actorId, companyId, roles);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw authenticationRequired();
        }
    }

    private ApiException authenticationRequired() {
        return new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
