package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentRequestDraftSeed;
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.document.domain.DocumentRequestReviewStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class DemoDocumentRequestDraftSeeder {

    private final DocumentRequestDraftRepository repository;
    private final JdbcTemplate jdbcTemplate;

    DemoDocumentRequestDraftSeeder(
            DocumentRequestDraftRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    void seed(DocumentRequestDraftSeed seed, DemoOperationalSeedContext context) {
        verifyReservedIdOwner(seed, context);
        var existing = repository.findByTaskIdAndCompanyId(seed.taskId(), context.companyId());
        if (existing.isPresent()) {
            verifyExisting(existing.get(), seed, context);
            return;
        }
        Instant createdAt = context.now().minus(seed.hoursAgo(), ChronoUnit.HOURS);
        repository.insert(new DocumentRequestDraft(
                seed.draftId(),
                seed.taskId(),
                context.companyId(),
                seed.language(),
                seed.documentTypes(),
                seed.message(),
                DocumentRequestReviewStatus.DRAFT,
                createdAt,
                createdAt,
                0L
        ));
    }

    void verifyExisting(DocumentRequestDraftSeed seed, DemoOperationalSeedContext context) {
        DocumentRequestDraft draft = repository.findByTaskIdAndCompanyId(
                        seed.taskId(),
                        context.companyId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "a reserved demo document request draft was not seeded"
                ));
        verifyExisting(draft, seed, context);
    }

    private void verifyReservedIdOwner(
            DocumentRequestDraftSeed seed,
            DemoOperationalSeedContext context
    ) {
        List<DraftOwner> owners = jdbcTemplate.query(
                "SELECT task_id, company_id FROM document_request_draft WHERE draft_id = ?",
                (resultSet, rowNumber) -> new DraftOwner(
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class)
                ),
                seed.draftId()
        );
        if (!owners.isEmpty()
                && (!seed.taskId().equals(owners.get(0).taskId())
                || !context.companyId().equals(owners.get(0).companyId()))) {
            throw new IllegalStateException(
                    "a reserved demo document request draft id belongs to a different task"
            );
        }
    }

    private void verifyExisting(
            DocumentRequestDraft draft,
            DocumentRequestDraftSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!seed.draftId().equals(draft.draftId())
                || !seed.taskId().equals(draft.taskId())
                || !context.companyId().equals(draft.companyId())
                || !seed.language().equals(draft.language())
                || !new HashSet<>(seed.documentTypes()).equals(new HashSet<>(draft.documentTypes()))
                || !seed.message().equals(draft.message())
                || draft.reviewStatus() != DocumentRequestReviewStatus.DRAFT) {
            throw new IllegalStateException(
                    "a reserved demo task already has different document request draft data"
            );
        }
    }

    private record DraftOwner(UUID taskId, UUID companyId) {
    }
}
