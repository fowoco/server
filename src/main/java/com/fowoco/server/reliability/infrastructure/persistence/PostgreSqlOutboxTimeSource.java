package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.OutboxTimeSource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "postgresql"
)
public class PostgreSqlOutboxTimeSource implements OutboxTimeSource {

    private final EntityManager entityManager;

    public PostgreSqlOutboxTimeSource(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Instant now() {
        Object result = entityManager.createNativeQuery(
                "SELECT pg_catalog.statement_timestamp()"
        ).getSingleResult();
        if (result instanceof Instant instant) {
            return instant;
        }
        if (result instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        throw new IllegalStateException("Unexpected PostgreSQL timestamp mapping.");
    }
}
