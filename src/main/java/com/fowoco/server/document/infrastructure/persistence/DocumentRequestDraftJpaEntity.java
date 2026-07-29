package com.fowoco.server.document.infrastructure.persistence;

import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.document.domain.DocumentRequestReviewStatus;
import com.fowoco.server.worker.domain.DocumentType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "document_request_draft")
public class DocumentRequestDraftJpaEntity {

    @Id
    @Column(name = "draft_id", nullable = false, updatable = false)
    private UUID draftId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "language", nullable = false, length = 20)
    private String language;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(
            name = "document_request_draft_type",
            joinColumns = @JoinColumn(name = "draft_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private List<DocumentType> documentTypes;

    @Column(name = "message", length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private DocumentRequestReviewStatus reviewStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DocumentRequestDraftJpaEntity() {
    }

    private DocumentRequestDraftJpaEntity(
            UUID draftId,
            UUID taskId,
            UUID companyId,
            String language,
            List<DocumentType> documentTypes,
            String message,
            DocumentRequestReviewStatus reviewStatus,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.draftId = draftId;
        this.taskId = taskId;
        this.companyId = companyId;
        this.language = language;
        this.documentTypes = documentTypes;
        this.message = message;
        this.reviewStatus = reviewStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static DocumentRequestDraftJpaEntity fromDomain(DocumentRequestDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        return new DocumentRequestDraftJpaEntity(
                draft.draftId(),
                draft.taskId(),
                draft.companyId(),
                draft.language(),
                draft.documentTypes(),
                draft.message(),
                draft.reviewStatus(),
                draft.createdAt(),
                draft.updatedAt(),
                draft.version()
        );
    }

    public DocumentRequestDraft toDomain() {
        return new DocumentRequestDraft(
                draftId,
                taskId,
                companyId,
                language,
                documentTypes,
                message,
                reviewStatus,
                createdAt,
                updatedAt,
                version
        );
    }

    public void applyState(DocumentRequestDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        if (!draftId.equals(draft.draftId())
                || !taskId.equals(draft.taskId())
                || !companyId.equals(draft.companyId())
                || !createdAt.equals(draft.createdAt())) {
            throw new IllegalArgumentException("immutable document request draft fields must not change");
        }
        if (version != draft.version()) {
            throw new IllegalArgumentException("document request draft version does not match");
        }
        this.language = draft.language();
        this.documentTypes = draft.documentTypes();
        this.message = draft.message();
        this.reviewStatus = draft.reviewStatus();
        this.updatedAt = draft.updatedAt();
    }
}
