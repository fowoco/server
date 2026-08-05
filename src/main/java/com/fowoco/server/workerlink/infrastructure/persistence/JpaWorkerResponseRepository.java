package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.domain.WorkerResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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

    @Override
    public void linkUpload(UUID responseId, UUID storedFileId, UUID companyId) {
        Objects.requireNonNull(responseId, "responseId must not be null");
        Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Query query = entityManager.createNativeQuery(
                "INSERT INTO worker_response_upload "
                        + "(response_id, stored_file_id, company_id) VALUES (?1, ?2, ?3)"
        );
        query.setParameter(1, responseId);
        query.setParameter(2, storedFileId);
        query.setParameter(3, companyId);
        query.executeUpdate();
    }

    @Override
    public boolean isUploadAlreadyLinked(UUID storedFileId, UUID companyId) {
        Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Long count = (Long) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM worker_response_upload "
                                + "WHERE stored_file_id = ?1 AND company_id = ?2"
                )
                .setParameter(1, storedFileId)
                .setParameter(2, companyId)
                .getSingleResult();
        return count != null && count > 0;
    }
}
