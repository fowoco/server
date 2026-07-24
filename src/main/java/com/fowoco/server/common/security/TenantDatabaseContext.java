package com.fowoco.server.common.security;

import java.util.UUID;

/**
 * Binds a trusted tenant identifier to the current database transaction.
 */
public interface TenantDatabaseContext {

    /**
     * Sets the company visible to tenant-protected database operations in the current transaction.
     *
     * @param companyId a company identifier obtained from a trusted authentication or bootstrap flow
     */
    void setCompanyIdForCurrentTransaction(UUID companyId);
}
