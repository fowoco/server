package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentFileLookup;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerDocumentRepository implements WorkerDocumentRepository, WorkerDocumentFileLookup {

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
                          and document.archivedAt is null
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
    public Optional<WorkerDocument> findByIdAndCompanyId(UUID workerDocumentId, UUID companyId) {
        Objects.requireNonNull(workerDocumentId, "workerDocumentId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select document
                        from WorkerDocumentJpaEntity document
                        where document.workerDocumentId = :workerDocumentId
                          and document.companyId = :companyId
                          and document.archivedAt is null
                        """,
                        WorkerDocumentJpaEntity.class
                )
                .setParameter("workerDocumentId", workerDocumentId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(WorkerDocumentJpaEntity::toDomain);
    }

    @Override
    public Optional<WorkerDocument> findByIdAndCompanyIdIncludingArchived(
            UUID workerDocumentId,
            UUID companyId
    ) {
        Objects.requireNonNull(workerDocumentId, "workerDocumentId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select document
                        from WorkerDocumentJpaEntity document
                        where document.workerDocumentId = :workerDocumentId
                          and document.companyId = :companyId
                        """,
                        WorkerDocumentJpaEntity.class
                )
                .setParameter("workerDocumentId", workerDocumentId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(WorkerDocumentJpaEntity::toDomain);
    }

    @Override
    public Optional<WorkerDocument> findByFileIdAndCompanyId(UUID fileId, UUID companyId) {
        Objects.requireNonNull(fileId, "fileId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select document
                        from WorkerDocumentJpaEntity document
                        where document.fileId = :fileId
                          and document.companyId = :companyId
                          and document.archivedAt is null
                        """,
                        WorkerDocumentJpaEntity.class
                )
                .setParameter("fileId", fileId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(WorkerDocumentJpaEntity::toDomain);
    }

    @Override
    public WorkerDocument update(WorkerDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        WorkerDocumentJpaEntity entity = entityManager.find(
                WorkerDocumentJpaEntity.class,
                document.workerDocumentId()
        );
        if (entity == null) {
            throw new IllegalStateException("worker document to update was not found");
        }
        entity.applyState(document);
        entityManager.flush();
        return entity.toDomain();
    }

    @Override
    public List<WorkerDocument> findPage(UUID companyId, WorkerDocumentSearchQuery query) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(query, "query must not be null");
        String jpql = "select document from WorkerDocumentJpaEntity document"
                + buildWhereClause(query)
                + " order by document.createdAt desc";
        TypedQuery<WorkerDocumentJpaEntity> jpaQuery = entityManager.createQuery(jpql, WorkerDocumentJpaEntity.class);
        bindParameters(jpaQuery, companyId, query);
        return jpaQuery
                .setFirstResult(query.page() * query.size())
                .setMaxResults(query.size())
                .getResultList()
                .stream()
                .map(WorkerDocumentJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countPage(UUID companyId, WorkerDocumentSearchQuery query) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(query, "query must not be null");
        String jpql = "select count(document) from WorkerDocumentJpaEntity document" + buildWhereClause(query);
        TypedQuery<Long> jpaQuery = entityManager.createQuery(jpql, Long.class);
        bindParameters(jpaQuery, companyId, query);
        return jpaQuery.getSingleResult();
    }

    private String buildWhereClause(WorkerDocumentSearchQuery query) {
        StringBuilder where = new StringBuilder(
                " where document.companyId = :companyId and document.archivedAt is null"
        );
        if (query.workerId() != null) {
            where.append(" and document.workerId = :workerId");
        }
        if (query.taskId() != null) {
            where.append(" and document.taskId = :taskId");
        }
        if (query.documentType() != null) {
            where.append(" and document.documentType = :documentType");
        }
        if (query.status() != null) {
            where.append(" and document.submissionStatus = :status");
        }
        if (query.expiryBefore() != null) {
            where.append(" and document.expiryDate < :expiryBefore");
        }
        return where.toString();
    }

    private void bindParameters(Query jpaQuery, UUID companyId, WorkerDocumentSearchQuery query) {
        jpaQuery.setParameter("companyId", companyId);
        if (query.workerId() != null) {
            jpaQuery.setParameter("workerId", query.workerId());
        }
        if (query.taskId() != null) {
            jpaQuery.setParameter("taskId", query.taskId());
        }
        if (query.documentType() != null) {
            jpaQuery.setParameter("documentType", query.documentType());
        }
        if (query.status() != null) {
            jpaQuery.setParameter("status", query.status());
        }
        if (query.expiryBefore() != null) {
            jpaQuery.setParameter("expiryBefore", query.expiryBefore());
        }
    }
}
