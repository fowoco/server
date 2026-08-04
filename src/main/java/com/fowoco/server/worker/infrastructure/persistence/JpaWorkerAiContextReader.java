package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import com.fowoco.server.worker.application.port.WorkerAiContextReader;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerAiContextReader implements WorkerAiContextReader {

    private final EntityManager entityManager;

    public JpaWorkerAiContextReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<WorkerAiContextSnapshot> findByDisplayName(UUID companyId, String displayName) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return entityManager.createQuery(
                        """
                        select worker
                        from WorkerJpaEntity worker
                        where worker.companyId = :companyId
                          and worker.displayName = :displayName
                        order by worker.workerId
                        """,
                        WorkerJpaEntity.class
                )
                .setParameter("companyId", companyId)
                .setParameter("displayName", displayName.strip())
                .setMaxResults(2)
                .getResultList()
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    private WorkerAiContextSnapshot toSnapshot(WorkerJpaEntity entity) {
        var worker = entity.toDomain();
        return new WorkerAiContextSnapshot(
                worker.workerId(),
                worker.companyId(),
                worker.displayName(),
                worker.nationalityCode(),
                worker.preferredLanguage(),
                worker.workStatus().name(),
                worker.stayExpiryDate(),
                worker.contractStartDate(),
                worker.contractEndDate()
        );
    }
}
