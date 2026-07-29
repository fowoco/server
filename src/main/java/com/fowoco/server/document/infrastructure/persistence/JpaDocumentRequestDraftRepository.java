package com.fowoco.server.document.infrastructure.persistence;

import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDocumentRequestDraftRepository implements DocumentRequestDraftRepository {

    private final EntityManager entityManager;

    public JpaDocumentRequestDraftRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(DocumentRequestDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        entityManager.persist(DocumentRequestDraftJpaEntity.fromDomain(draft));
        entityManager.flush();
    }

    @Override
    public Optional<DocumentRequestDraft> findByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        return entityManager.createQuery(
                        """
                        select draft
                        from DocumentRequestDraftJpaEntity draft
                        where draft.taskId = :taskId
                          and draft.companyId = :companyId
                        """,
                        DocumentRequestDraftJpaEntity.class
                )
                .setParameter("taskId", taskId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(DocumentRequestDraftJpaEntity::toDomain);
    }

    @Override
    public DocumentRequestDraft update(DocumentRequestDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        DocumentRequestDraftJpaEntity entity = entityManager.find(
                DocumentRequestDraftJpaEntity.class,
                draft.draftId()
        );
        if (entity == null) {
            throw new IllegalStateException("document request draft to update was not found");
        }
        entity.applyState(draft);
        entityManager.flush();
        return entity.toDomain();
    }
}
