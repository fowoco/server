package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.port.AuthTenantBootstrap;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * H2/local bootstrap adapter used where PostgreSQL SECURITY DEFINER functions are unavailable.
 */
@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "transaction-only",
        matchIfMissing = true
)
public class JpaAuthTenantBootstrap implements AuthTenantBootstrap {

    private final EntityManager entityManager;

    public JpaAuthTenantBootstrap(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<UUID> findCompanyIdByNormalizedEmail(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
        return entityManager.createQuery(
                        """
                        select account.companyId
                        from UserAccountJpaEntity account
                        where account.normalizedEmail = :normalizedEmail
                        """,
                        UUID.class
                )
                .setParameter("normalizedEmail", normalizedEmail)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public Optional<UUID> findCompanyIdByRefreshTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        return entityManager.createQuery(
                        """
                        select token.companyId
                        from RefreshTokenJpaEntity token
                        where token.tokenHash = :tokenHash
                        """,
                        UUID.class
                )
                .setParameter("tokenHash", tokenHash)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }
}
