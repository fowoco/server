package com.fowoco.server.common.security;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * PostgreSQL tenant context backed by a transaction-local custom setting.
 */
@Component
public final class PostgreSqlTenantDatabaseContext implements TenantDatabaseContext {

    private static final String READ_COMPANY_ID_SQL = """
            SELECT NULLIF(pg_catalog.current_setting('app.company_id', true), '')
            """;
    private static final String SET_COMPANY_ID_SQL = """
            SELECT pg_catalog.set_config('app.company_id', ?1, true)
            """;

    private final EntityManager entityManager;

    public PostgreSqlTenantDatabaseContext(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void setCompanyIdForCurrentTransaction(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Tenant database context requires an active transaction."
            );
        }
        if (!entityManager.isJoinedToTransaction()) {
            throw new IllegalStateException(
                    "Tenant database context requires a transaction-bound database connection."
            );
        }

        String requestedCompanyId = companyId.toString();
        Object currentCompanyValue = entityManager.createNativeQuery(READ_COMPANY_ID_SQL)
                .getSingleResult();
        String currentCompanyId = currentCompanyValue == null
                ? null
                : currentCompanyValue.toString();
        if (currentCompanyId != null && !currentCompanyId.equals(requestedCompanyId)) {
            throw new IllegalStateException(
                    "Tenant database context cannot change within a transaction."
            );
        }

        String appliedCompanyId = entityManager.createNativeQuery(SET_COMPANY_ID_SQL)
                .setParameter(1, requestedCompanyId)
                .getSingleResult()
                .toString();
        if (!requestedCompanyId.equals(appliedCompanyId)) {
            throw new IllegalStateException("Tenant database context was not applied.");
        }
    }
}
