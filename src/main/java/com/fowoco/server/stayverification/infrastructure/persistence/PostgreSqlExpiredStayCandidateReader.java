package com.fowoco.server.stayverification.infrastructure.persistence;

import com.fowoco.server.stayverification.application.port.ExpiredStayCandidateReader;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository.ExpiredWorker;
import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "postgresql"
)
public class PostgreSqlExpiredStayCandidateReader implements ExpiredStayCandidateReader {

    private static final String SQL = """
            SELECT company_id, worker_id, display_name, stay_expiry_date
              FROM public.bootstrap_expired_stay_candidates(?1)
            """;

    private final EntityManager entityManager;

    public PostgreSqlExpiredStayCandidateReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ExpiredWorker> findExpiredWorkers(LocalDate today) {
        return ((List<Object[]>) entityManager.createNativeQuery(SQL)
                .setParameter(1, today)
                .getResultList()).stream()
                .map(row -> new ExpiredWorker(
                        uuid(row[0]),
                        uuid(row[1]),
                        row[2].toString(),
                        row[3] instanceof LocalDate date
                                ? date
                                : ((Date) row[3]).toLocalDate()
                ))
                .toList();
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }
}
