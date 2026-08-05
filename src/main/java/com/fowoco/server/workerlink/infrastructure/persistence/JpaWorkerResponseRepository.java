package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.application.port.WorkerResponseUploadAlreadyLinkedException;
import com.fowoco.server.workerlink.domain.WorkerResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerResponseRepository implements WorkerResponseRepository {

    private static final String UNIQUE_UPLOAD_FILE_CONSTRAINT =
            "uq_worker_response_upload_file_company";

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
        try {
            query.executeUpdate();
        } catch (RuntimeException exception) {
            if (isUniqueUploadFileViolation(exception)) {
                throw new WorkerResponseUploadAlreadyLinkedException(exception);
            }
            throw exception;
        }
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

    static boolean isUniqueUploadFileViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && containsConstraintName(constraintViolation.getConstraintName())) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && containsConstraintName(sqlException.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsConstraintName(String value) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(UNIQUE_UPLOAD_FILE_CONSTRAINT);
    }
}
