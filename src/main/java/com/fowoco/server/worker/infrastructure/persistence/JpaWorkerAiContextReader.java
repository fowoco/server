package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import com.fowoco.server.worker.application.WorkerIdentityDocumentStatuses;
import com.fowoco.server.worker.application.port.WorkerAiContextReader;
import com.fowoco.server.worker.application.port.WorkerIdentityDocumentStatusReader;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import jakarta.persistence.EntityManager;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaWorkerAiContextReader implements
        WorkerAiContextReader,
        WorkerIdentityDocumentStatusReader {

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
        return entityManager.createQuery(
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
                .getResultList()
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Override
    public WorkerIdentityDocumentStatuses findCurrentStatuses(UUID companyId, UUID workerId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        List<Object[]> rows = entityManager.createQuery(
                        """
                        select document.documentType, document.submissionStatus
                        from WorkerDocumentJpaEntity document
                        where document.companyId = :companyId
                          and document.workerId = :workerId
                          and document.documentType in :documentTypes
                        order by document.updatedAt desc,
                                 document.createdAt desc,
                                 document.workerDocumentId desc
                        """,
                        Object[].class
                )
                .setParameter("companyId", companyId)
                .setParameter("workerId", workerId)
                .setParameter("documentTypes", List.of(DocumentType.PASSPORT_COPY, DocumentType.ARC))
                .getResultList();

        Map<DocumentType, SubmissionStatus> latest = new EnumMap<>(DocumentType.class);
        for (Object[] row : rows) {
            latest.putIfAbsent((DocumentType) row[0], (SubmissionStatus) row[1]);
        }
        return new WorkerIdentityDocumentStatuses(
                latest.getOrDefault(DocumentType.PASSPORT_COPY, SubmissionStatus.MISSING),
                latest.getOrDefault(DocumentType.ARC, SubmissionStatus.MISSING)
        );
    }

    private WorkerAiContextSnapshot toSnapshot(WorkerJpaEntity entity) {
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
                findCurrentStatuses(worker.companyId(), worker.workerId())
        );
    }
}
