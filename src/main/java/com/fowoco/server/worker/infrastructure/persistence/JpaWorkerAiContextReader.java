package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import com.fowoco.server.worker.application.WorkerDisplayNameNormalizer;
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
    private final WorkerDisplayNameNormalizer displayNameNormalizer;

    public JpaWorkerAiContextReader(
            EntityManager entityManager,
            WorkerDisplayNameNormalizer displayNameNormalizer
    ) {
        this.entityManager = entityManager;
        this.displayNameNormalizer = displayNameNormalizer;
    }

    @Override
    public List<WorkerAiContextSnapshot> findByDisplayName(UUID companyId, String displayName) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        String strippedDisplayName = displayName.strip();
        List<WorkerJpaEntity> exactMatches = entityManager.createQuery(
                        """
                        select worker
                        from WorkerJpaEntity worker
                        where worker.companyId = :companyId
                          and worker.displayName = :displayName
                          and not exists (
                              select archive.workerId
                              from WorkerArchiveJpaEntity archive
                              where archive.workerId = worker.workerId
                                and archive.companyId = worker.companyId
                          )
                        order by worker.workerId
                        """,
                        WorkerJpaEntity.class
                )
                .setParameter("companyId", companyId)
                .setParameter("displayName", strippedDisplayName)
                .setMaxResults(2)
                .getResultList();
        if (!exactMatches.isEmpty()) {
            return exactMatches.stream()
                    .map(this::toSnapshot)
                    .toList();
        }

        String lookupKey;
        try {
            lookupKey = displayNameNormalizer.normalize(strippedDisplayName);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        List<UUID> workerIds = entityManager.createQuery(
                        """
                        select worker.workerId, worker.displayName
                        from WorkerJpaEntity worker
                        where worker.companyId = :companyId
                          and not exists (
                              select archive.workerId
                              from WorkerArchiveJpaEntity archive
                              where archive.workerId = worker.workerId
                                and archive.companyId = worker.companyId
                          )
                        order by worker.workerId
                        """,
                        Object[].class
                )
                .setParameter("companyId", companyId)
                .getResultList()
                .stream()
                .filter(row -> lookupKey.equals(displayNameNormalizer.normalize((String) row[1])))
                .limit(2)
                .map(row -> (UUID) row[0])
                .toList();
        if (workerIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                        select worker
                        from WorkerJpaEntity worker
                        where worker.companyId = :companyId
                          and worker.workerId in :workerIds
                          and not exists (
                              select archive.workerId
                              from WorkerArchiveJpaEntity archive
                              where archive.workerId = worker.workerId
                                and archive.companyId = worker.companyId
                          )
                        order by worker.workerId
                        """,
                        WorkerJpaEntity.class
                )
                .setParameter("companyId", companyId)
                .setParameter("workerIds", workerIds)
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
                          and document.archivedAt is null
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
