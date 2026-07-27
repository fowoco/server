package com.fowoco.server.auth.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves only the tenant identifier needed to enter an authentication transaction.
 */
public interface AuthTenantBootstrap {

    Optional<UUID> findCompanyIdByNormalizedEmail(String normalizedEmail);

    Optional<UUID> findCompanyIdByRefreshTokenHash(String tokenHash);
}
