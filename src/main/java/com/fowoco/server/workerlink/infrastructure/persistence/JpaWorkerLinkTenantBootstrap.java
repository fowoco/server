package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.application.port.WorkerLinkTenantBootstrap;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "transaction-only",
        matchIfMissing = true
)
public class JpaWorkerLinkTenantBootstrap implements WorkerLinkTenantBootstrap {

    private final EntityManager entityManager;

    public JpaWorkerLinkTenantBootstrap(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<UUID> findCompanyIdByWorkerLinkTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        return entityManager.createQuery(
                        """
                        select link.companyId
                        from WorkerLinkJpaEntity link
                        where link.tokenHash = :tokenHash
                        """,
                        UUID.class
                )
                .setParameter("tokenHash", tokenHash)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }
}
