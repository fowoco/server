package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.port.AuthTenantBootstrap;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL bootstrap adapter restricted to company-id-only SECURITY DEFINER functions.
 */
@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "postgresql"
)
public class PostgreSqlAuthTenantBootstrap implements AuthTenantBootstrap {

    private static final String EMAIL_BOOTSTRAP_SQL = """
            SELECT public.bootstrap_company_id_by_normalized_email(?1)
            """;
    private static final String REFRESH_TOKEN_BOOTSTRAP_SQL = """
            SELECT public.bootstrap_company_id_by_refresh_token_hash(?1)
            """;
    private static final String PASSWORD_RESET_TOKEN_BOOTSTRAP_SQL = """
            SELECT public.bootstrap_company_id_by_password_reset_token_hash(?1)
            """;

    private final EntityManager entityManager;

    public PostgreSqlAuthTenantBootstrap(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<UUID> findCompanyIdByNormalizedEmail(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
        return queryCompanyId(EMAIL_BOOTSTRAP_SQL, normalizedEmail);
    }

    @Override
    public Optional<UUID> findCompanyIdByRefreshTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        return queryCompanyId(REFRESH_TOKEN_BOOTSTRAP_SQL, tokenHash);
    }

    @Override
    public Optional<UUID> findCompanyIdByPasswordResetTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        return queryCompanyId(PASSWORD_RESET_TOKEN_BOOTSTRAP_SQL, tokenHash);
    }

    private Optional<UUID> queryCompanyId(String sql, String lookupValue) {
        Object result = entityManager.createNativeQuery(sql)
                .setParameter(1, lookupValue)
                .getSingleResult();
        if (result == null) {
            return Optional.empty();
        }
        if (result instanceof UUID companyId) {
            return Optional.of(companyId);
        }
        return Optional.of(UUID.fromString(result.toString()));
    }
}
