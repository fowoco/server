package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerLinkStatus;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerLinkRepository implements WorkerLinkRepository {

    private final EntityManager entityManager;

    public JpaWorkerLinkRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(WorkerLink workerLink) {
        Objects.requireNonNull(workerLink, "workerLink must not be null");
        entityManager.persist(WorkerLinkJpaEntity.fromDomain(workerLink));
        entityManager.flush();
    }

    @Override
    public WorkerLink update(WorkerLink workerLink) {
        Objects.requireNonNull(workerLink, "workerLink must not be null");
        WorkerLinkJpaEntity entity = entityManager.find(WorkerLinkJpaEntity.class, workerLink.workerLinkId());
        if (entity == null) {
            throw new IllegalStateException("worker link to update was not found");
        }
        entity.applyState(workerLink);
        entityManager.flush();
        return entity.toDomain();
    }

    @Override
    public Optional<WorkerLink> findByTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        return entityManager.createQuery(
                        """
                        select link
                        from WorkerLinkJpaEntity link
                        where link.tokenHash = :tokenHash
                        """,
                        WorkerLinkJpaEntity.class
                )
                .setParameter("tokenHash", tokenHash)
                .getResultStream()
                .findFirst()
                .map(WorkerLinkJpaEntity::toDomain);
    }

    @Override
    public Optional<WorkerLink> findActiveByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select link
                        from WorkerLinkJpaEntity link
                        where link.taskId = :taskId
                          and link.companyId = :companyId
                          and link.status = :status
                        """,
                        WorkerLinkJpaEntity.class
                )
                .setParameter("taskId", taskId)
                .setParameter("companyId", companyId)
                .setParameter("status", WorkerLinkStatus.ACTIVE)
                .getResultStream()
                .findFirst()
                .map(WorkerLinkJpaEntity::toDomain);
    }
}
