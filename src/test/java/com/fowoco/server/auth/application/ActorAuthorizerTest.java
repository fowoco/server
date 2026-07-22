package com.fowoco.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorAuthorizerTest {

    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_COMPANY_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private final ActorAuthorizer actorAuthorizer = new ActorAuthorizer();

    @Test
    void adminAndHrCanPerformHrWriteActions() {
        assertThatCode(() -> actorAuthorizer.requireHrWrite(actor(UserRole.ADMIN)))
                .doesNotThrowAnyException();
        assertThatCode(() -> actorAuthorizer.requireHrWrite(actor(UserRole.HR)))
                .doesNotThrowAnyException();
    }

    @Test
    void viewerCannotPerformHrWriteActions() {
        assertThatThrownBy(() -> actorAuthorizer.requireHrWrite(actor(UserRole.VIEWER)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)
                );
    }

    @Test
    void resourceFromAnotherCompanyIsHidden() {
        assertThatThrownBy(() -> actorAuthorizer.requireSameCompany(
                actor(UserRole.ADMIN),
                OTHER_COMPANY_ID
        ))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
                );
    }

    @Test
    void actorRolesAreDefensivelyCopied() {
        Set<UserRole> mutableRoles = EnumSet.of(UserRole.HR);
        ActorContext actorContext = new ActorContext(ACTOR_ID, COMPANY_ID, mutableRoles);

        mutableRoles.clear();

        assertThat(actorContext.roles()).containsExactly(UserRole.HR);
        assertThatThrownBy(() -> actorContext.roles().add(UserRole.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ActorContext actor(UserRole role) {
        return new ActorContext(ACTOR_ID, COMPANY_ID, Set.of(role));
    }
}
