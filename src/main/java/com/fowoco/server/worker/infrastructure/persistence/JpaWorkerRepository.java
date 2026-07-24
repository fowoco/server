package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerRepository implements WorkerRepository {

    private final EntityManager entityManager;

    public JpaWorkerRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(Worker worker) {
        Objects.requireNonNull(worker, "worker must not be null");
        entityManager.persist(WorkerJpaEntity.fromDomain(worker));
        entityManager.flush();
    }

    @Override
    public Optional<Worker> findByWorkerIdAndCompanyId(UUID workerId, UUID companyId) {
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select worker
                        from WorkerJpaEntity worker
                        where worker.workerId = :workerId
                          and worker.companyId = :companyId
                        """,
                        WorkerJpaEntity.class
                )
                .setParameter("workerId", workerId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(WorkerJpaEntity::toDomain);
    }

    @Override
    public Worker update(Worker worker) {
        Objects.requireNonNull(worker, "worker must not be null");
        WorkerJpaEntity entity = entityManager.find(WorkerJpaEntity.class, worker.workerId());
        if (entity == null) {
            throw new IllegalStateException("worker to update was not found");
        }
        entity.applyState(worker);
        entityManager.flush();
        return entity.toDomain();
    }
}
