package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.OutboxClaimBootstrap;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL claim adapter returning only identifiers from the restricted bootstrap function.
 */
@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "postgresql"
)
public class PostgreSqlOutboxClaimBootstrap implements OutboxClaimBootstrap {

    private static final String CLAIM_SQL = """
            SELECT event_id, company_id, review_required
            FROM public.bootstrap_claim_event_publications(?1, ?2, ?3, ?4, ?5)
            """;

    private final EntityManager entityManager;

    public PostgreSqlOutboxClaimBootstrap(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<ClaimResult> claim(
            String owner,
            Instant now,
            Duration leaseDuration,
            int batchSize,
            int maxAttempts
    ) {
        Instant leaseExpiresAt = now.plus(leaseDuration);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(CLAIM_SQL)
                .setParameter(1, owner)
                .setParameter(2, now)
                .setParameter(3, leaseExpiresAt)
                .setParameter(4, batchSize)
                .setParameter(5, maxAttempts)
                .getResultList();
        return rows.stream()
                .map(row -> new ClaimResult(
                        asUuid(row[0]),
                        asUuid(row[1]),
                        (Boolean) row[2]
                ))
                .toList();
    }

    private UUID asUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}
