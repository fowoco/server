package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.domain.WorkerResponse;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerResponseRepository implements WorkerResponseRepository {

    private final EntityManager entityManager;

    public JpaWorkerResponseRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(WorkerResponse workerResponse) {
        Objects.requireNonNull(workerResponse, "workerResponse must not be null");
        entityManager.persist(WorkerResponseJpaEntity.fromDomain(workerResponse));
        entityManager.flush();
    }

    @Override
    public Optional<WorkerResponse> findByWorkerLinkIdAndIdempotencyKey(UUID workerLinkId, String idempotencyKey) {
        Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        return entityManager.createQuery(
                        """
                        select response
                        from WorkerResponseJpaEntity response
                        where response.workerLinkId = :workerLinkId
                          and response.idempotencyKey = :idempotencyKey
                        """,
                        WorkerResponseJpaEntity.class
                )
                .setParameter("workerLinkId", workerLinkId)
                .setParameter("idempotencyKey", idempotencyKey)
                .getResultStream()
                .findFirst()
                .map(WorkerResponseJpaEntity::toDomain);
    }
}
