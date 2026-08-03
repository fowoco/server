package com.fowoco.server.common.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Validates tenant transaction boundaries on databases that do not support PostgreSQL settings.
 */
@Component
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "transaction-only",
        matchIfMissing = true
)
public final class TransactionOnlyTenantDatabaseContext implements TenantDatabaseContext {

    @Override
    public void setCompanyIdForCurrentTransaction(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Tenant database context requires an active transaction."
            );
        }
    }
}
