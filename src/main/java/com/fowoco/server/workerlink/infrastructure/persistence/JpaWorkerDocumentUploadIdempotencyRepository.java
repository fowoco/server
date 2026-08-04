package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.application.port.WorkerDocumentUploadIdempotencyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerDocumentUploadIdempotencyRepository implements WorkerDocumentUploadIdempotencyRepository {

    private final EntityManager entityManager;

    public JpaWorkerDocumentUploadIdempotencyRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<UUID> findStoredFileId(UUID workerLinkId, String clientRequestId) {
        Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        Objects.requireNonNull(clientRequestId, "clientRequestId must not be null");
        return entityManager.createNativeQuery(
                        "SELECT CAST(stored_file_id AS VARCHAR) FROM worker_document_upload_idempotency "
                                + "WHERE worker_link_id = ?1 AND client_request_id = ?2"
                )
                .setParameter(1, workerLinkId)
                .setParameter(2, clientRequestId)
                .getResultStream()
                .findFirst()
                .map(result -> UUID.fromString(result.toString()));
    }

    @Override
    public void save(UUID workerLinkId, String clientRequestId, UUID storedFileId) {
        Objects.requireNonNull(workerLinkId, "workerLinkId must not be null");
        Objects.requireNonNull(clientRequestId, "clientRequestId must not be null");
        Objects.requireNonNull(storedFileId, "storedFileId must not be null");
        Query query = entityManager.createNativeQuery(
                "INSERT INTO worker_document_upload_idempotency "
                        + "(worker_link_id, client_request_id, stored_file_id) VALUES (?1, ?2, ?3)"
        );
        query.setParameter(1, workerLinkId);
        query.setParameter(2, clientRequestId);
        query.setParameter(3, storedFileId);
        query.executeUpdate();
    }
}
