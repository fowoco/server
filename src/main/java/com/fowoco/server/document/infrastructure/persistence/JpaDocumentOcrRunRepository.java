package com.fowoco.server.document.infrastructure.persistence;

import com.fowoco.server.document.application.port.DocumentOcrRunRepository;
import com.fowoco.server.document.domain.DocumentOcrRun;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDocumentOcrRunRepository implements DocumentOcrRunRepository {

    private final EntityManager entityManager;

    public JpaDocumentOcrRunRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(DocumentOcrRun run) {
        entityManager.persist(DocumentOcrRunJpaEntity.fromDomain(run));
        entityManager.flush();
    }

    @Override
    public Optional<DocumentOcrRun> findByIdAndCompanyId(UUID ocrRunId, UUID companyId) {
        return findOne(
                "where run.ocrRunId = :value and run.companyId = :companyId",
                "value",
                ocrRunId,
                companyId
        );
    }

    @Override
    public Optional<DocumentOcrRun> findByIdempotencyKeyHashAndCompanyId(String keyHash, UUID companyId) {
        return findOne(
                "where run.idempotencyKeyHash = :value and run.companyId = :companyId",
                "value",
                keyHash,
                companyId
        );
    }

    @Override
    public Optional<DocumentOcrRun> findLatestByDocumentIdAndCompanyId(UUID documentId, UUID companyId) {
        return entityManager.createQuery(
                        """
                        select run from DocumentOcrRunJpaEntity run
                        where run.workerDocumentId = :documentId
                          and run.companyId = :companyId
                        order by run.createdAt desc, run.ocrRunId desc
                        """,
                        DocumentOcrRunJpaEntity.class
                )
                .setParameter("documentId", documentId)
                .setParameter("companyId", companyId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(DocumentOcrRunJpaEntity::toDomain);
    }

    @Override
    public DocumentOcrRun update(DocumentOcrRun run) {
        DocumentOcrRunJpaEntity entity = entityManager.find(DocumentOcrRunJpaEntity.class, run.ocrRunId());
        if (entity == null) {
            throw new IllegalStateException("OCR run to update was not found");
        }
        entity.applyState(run);
        entityManager.flush();
        return entity.toDomain();
    }

    private Optional<DocumentOcrRun> findOne(
            String where,
            String parameterName,
            Object value,
            UUID companyId
    ) {
        Objects.requireNonNull(value, "query value must not be null");
        return entityManager.createQuery(
                        "select run from DocumentOcrRunJpaEntity run " + where,
                        DocumentOcrRunJpaEntity.class
                )
                .setParameter(parameterName, value)
                .setParameter("companyId", companyId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(DocumentOcrRunJpaEntity::toDomain);
    }
}
