package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.application.port.WorkerDocumentUploadIdempotencyRepository;
import com.fowoco.server.workerlink.application.port.WorkerDocumentUploadIdempotencyRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerDocumentUploadIdempotencyRepository implements WorkerDocumentUploadIdempotencyRepository {

    private static final String CANONICAL_CLIENT_REQUEST_ID_PREFIX = "canonical:";

    private final EntityManager entityManager;

    public JpaWorkerDocumentUploadIdempotencyRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<WorkerDocumentUploadIdempotencyRecord> findByKeyHash(
            UUID workerLinkId,
            UUID companyId,
            String idempotencyKeyHash
    ) {
        Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(idempotencyKeyHash, "idempotencyKeyHash must not be null");
        return entityManager.createNativeQuery(
                        "SELECT CAST(stored_file_id AS VARCHAR), request_hash "
                                + "FROM worker_document_upload_idempotency "
                                + "WHERE worker_link_id = ?1 AND company_id = ?2 "
                                + "AND idempotency_key_hash = ?3"
                )
                .setParameter(1, workerLinkId)
                .setParameter(2, companyId)
                .setParameter(3, idempotencyKeyHash)
                .getResultStream()
                .findFirst()
                .map(result -> {
                    Object[] columns = (Object[]) result;
                    return new WorkerDocumentUploadIdempotencyRecord(
                            UUID.fromString(columns[0].toString()),
                            columns[1].toString()
                    );
                });
    }

    @Override
    public void save(
            UUID workerLinkId,
            UUID companyId,
            String idempotencyKeyHash,
            String requestHash,
            UUID storedFileId
    ) {
        Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(idempotencyKeyHash, "idempotencyKeyHash must not be null");
        Objects.requireNonNull(requestHash, "requestHash must not be null");
        Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        String compatibilityClientRequestId = CANONICAL_CLIENT_REQUEST_ID_PREFIX + storedFileId;
        Query query = entityManager.createNativeQuery(
                "INSERT INTO worker_document_upload_idempotency "
                        + "(worker_link_id, company_id, client_request_id, stored_file_id, "
                        + "idempotency_key_hash, request_hash) "
                        + "VALUES (?1, ?2, ?3, ?4, ?5, ?6)"
        );
        query.setParameter(1, workerLinkId);
        query.setParameter(2, companyId);
        query.setParameter(3, compatibilityClientRequestId);
        query.setParameter(4, storedFileId);
        query.setParameter(5, idempotencyKeyHash);
        query.setParameter(6, requestHash);
        query.executeUpdate();
    }
}
