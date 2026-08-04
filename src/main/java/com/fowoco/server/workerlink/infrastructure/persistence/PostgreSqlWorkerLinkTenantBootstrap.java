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
        havingValue = "postgresql"
)
public class PostgreSqlWorkerLinkTenantBootstrap implements WorkerLinkTenantBootstrap {

    private static final String WORKER_LINK_BOOTSTRAP_SQL = """
            SELECT public.bootstrap_company_id_by_worker_link_token_hash(?1)
            """;

    private final EntityManager entityManager;

    public PostgreSqlWorkerLinkTenantBootstrap(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<UUID> findCompanyIdByWorkerLinkTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Object result = entityManager.createNativeQuery(WORKER_LINK_BOOTSTRAP_SQL)
                .setParameter(1, tokenHash)
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
