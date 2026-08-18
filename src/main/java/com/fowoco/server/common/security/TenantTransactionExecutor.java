package com.fowoco.server.common.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes work in an independent transaction bound to one trusted tenant.
 */
@Component
public final class TenantTransactionExecutor {

    private final TransactionTemplate transactionTemplate;
    private final TenantDatabaseContext tenantDatabaseContext;

    public TenantTransactionExecutor(
            PlatformTransactionManager transactionManager,
            TenantDatabaseContext tenantDatabaseContext
    ) {
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(
                transactionManager,
                "transactionManager must not be null"
        ));
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.tenantDatabaseContext = Objects.requireNonNull(
                tenantDatabaseContext,
                "tenantDatabaseContext must not be null"
        );
    }

    public void execute(UUID companyId, Runnable callback) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        transactionTemplate.executeWithoutResult(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
            callback.run();
        });
    }
}
