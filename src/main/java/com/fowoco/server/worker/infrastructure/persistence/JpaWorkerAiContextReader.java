package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import com.fowoco.server.worker.application.WorkerDocumentAiContextSnapshot;
import com.fowoco.server.worker.application.port.WorkerAiContextReader;
import com.fowoco.server.worker.domain.DocumentType;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerAiContextReader implements WorkerAiContextReader {

    private final EntityManager entityManager;

    public JpaWorkerAiContextReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<WorkerAiContextSnapshot> findByDisplayName(UUID companyId, String displayName) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        List<WorkerJpaEntity> workers = entityManager.createQuery(
                        """
                        select worker
                        from WorkerJpaEntity worker
                        where worker.companyId = :companyId
                          and worker.displayName = :displayName
                        order by worker.workerId
                        """,
                        WorkerJpaEntity.class
                )
                .setParameter("companyId", companyId)
                .setParameter("displayName", displayName.strip())
                .setMaxResults(2)
                .getResultList();
        if (workers.isEmpty()) {
            return List.of();
        }
        List<UUID> workerIds = workers.stream()
                .map(entity -> entity.toDomain().workerId())
                .toList();
        Map<UUID, Map<DocumentType, WorkerDocumentAiContextSnapshot>> documentsByWorker =
                new LinkedHashMap<>();
        entityManager.createQuery(
                        """
                        select document
                        from WorkerDocumentJpaEntity document
                        where document.companyId = :companyId
                          and document.workerId in :workerIds
                          and document.documentType in :documentTypes
                        order by document.updatedAt desc, document.workerDocumentId
                        """,
                        WorkerDocumentJpaEntity.class
                )
                .setParameter("companyId", companyId)
                .setParameter("workerIds", workerIds)
                .setParameter(
                        "documentTypes",
                        List.of(DocumentType.PASSPORT_COPY, DocumentType.ARC)
                )
                .getResultList()
                .forEach(entity -> {
                    var document = entity.toDomain();
                    documentsByWorker
                            .computeIfAbsent(document.workerId(), ignored -> new LinkedHashMap<>())
                            .putIfAbsent(
                                    document.documentType(),
                                    new WorkerDocumentAiContextSnapshot(
                                            document.documentType(),
                                            document.submissionStatus(),
                                            document.expiryDate()
                                    )
                            );
                });
        return workers.stream()
                .map(worker -> toSnapshot(
                        worker,
                        documentsByWorker.getOrDefault(worker.toDomain().workerId(), Map.of())
                ))
                .toList();
    }

    private WorkerAiContextSnapshot toSnapshot(
            WorkerJpaEntity entity,
            Map<DocumentType, WorkerDocumentAiContextSnapshot> documents
    ) {
        var worker = entity.toDomain();
        return new WorkerAiContextSnapshot(
                worker.workerId(),
                worker.companyId(),
                worker.displayName(),
                worker.nationalityCode(),
                worker.preferredLanguage(),
                worker.workStatus().name(),
                worker.stayExpiryDate(),
                worker.contractStartDate(),
                worker.contractEndDate(),
                documents
        );
    }
}
