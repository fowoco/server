package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.WorkerSearchQuery;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
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

    @Override
    public List<Worker> findPage(UUID companyId, WorkerSearchQuery query) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(query, "query must not be null");
        TypedQuery<WorkerJpaEntity> jpaQuery = entityManager.createQuery(
                buildWhereClause(query) + " order by worker.createdAt desc",
                WorkerJpaEntity.class
        );
        bindParameters(jpaQuery, companyId, query);
        return jpaQuery
                .setFirstResult(query.page() * query.size())
                .setMaxResults(query.size())
                .getResultList()
                .stream()
                .map(WorkerJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countPage(UUID companyId, WorkerSearchQuery query) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(query, "query must not be null");
        TypedQuery<Long> jpaQuery = entityManager.createQuery(
                "select count(worker) " + buildWhereClause(query).replaceFirst("^select worker ", ""),
                Long.class
        );
        bindParameters(jpaQuery, companyId, query);
        return jpaQuery.getSingleResult();
    }

    private String buildWhereClause(WorkerSearchQuery query) {
        StringBuilder jpql = new StringBuilder("select worker from WorkerJpaEntity worker where worker.companyId = :companyId");
        if (query.status() != null) {
            jpql.append(" and worker.workStatus = :status");
        }
        if (query.language() != null) {
            jpql.append(" and worker.preferredLanguage = :language");
        }
        if (query.expiryBefore() != null) {
            jpql.append(" and worker.stayExpiryDate < :expiryBefore");
        }
        return jpql.toString();
    }

    private void bindParameters(
            jakarta.persistence.Query jpaQuery,
            UUID companyId,
            WorkerSearchQuery query
    ) {
        jpaQuery.setParameter("companyId", companyId);
        if (query.status() != null) {
            jpaQuery.setParameter("status", query.status());
        }
        if (query.language() != null) {
            jpaQuery.setParameter("language", query.language());
        }
        if (query.expiryBefore() != null) {
            jpaQuery.setParameter("expiryBefore", query.expiryBefore());
        }
    }
}
