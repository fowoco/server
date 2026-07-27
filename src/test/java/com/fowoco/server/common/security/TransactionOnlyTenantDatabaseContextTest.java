package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionOnlyTenantDatabaseContextTest {

    private final TenantDatabaseContext tenantDatabaseContext =
            new TransactionOnlyTenantDatabaseContext();

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void rejectsTenantBindingOutsideATransaction() {
        assertThatThrownBy(() -> tenantDatabaseContext.setCompanyIdForCurrentTransaction(
                UUID.randomUUID()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    void acceptsTrustedTenantInsideATransactionBoundary() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatCode(() -> tenantDatabaseContext.setCompanyIdForCurrentTransaction(
                UUID.randomUUID()
        )).doesNotThrowAnyException();
    }
}
