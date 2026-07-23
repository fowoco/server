package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerDocumentRepository implements WorkerDocumentRepository {

    private final EntityManager entityManager;

    public JpaWorkerDocumentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(WorkerDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        entityManager.persist(WorkerDocumentJpaEntity.fromDomain(document));
        entityManager.flush();
    }

    @Override
    public Optional<WorkerDocument> findByIdAndWorkerIdAndCompanyId(
            UUID workerDocumentId,
            UUID workerId,
            UUID companyId
    ) {
        Objects.requireNonNull(workerDocumentId, "workerDocumentId must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select document
                        from WorkerDocumentJpaEntity document
                        where document.workerDocumentId = :workerDocumentId
                          and document.workerId = :workerId
                          and document.companyId = :companyId
                        """,
                        WorkerDocumentJpaEntity.class
                )
                .setParameter("workerDocumentId", workerDocumentId)
                .setParameter("workerId", workerId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(WorkerDocumentJpaEntity::toDomain);
    }

    @Override
    public void update(WorkerDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        WorkerDocumentJpaEntity entity = entityManager.find(
                WorkerDocumentJpaEntity.class,
                document.workerDocumentId()
        );
        if (entity == null) {
            throw new IllegalStateException("worker document to update was not found");
        }
        entity.applyState(document);
    }
}
