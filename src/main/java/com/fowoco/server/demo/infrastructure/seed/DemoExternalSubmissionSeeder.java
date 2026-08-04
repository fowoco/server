package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.approval.application.port.ExternalSubmissionRepository;
import com.fowoco.server.approval.domain.ExternalSubmission;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ExternalSubmissionSeed;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class DemoExternalSubmissionSeeder {

    private final ExternalSubmissionRepository repository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    DemoExternalSubmissionSeeder(
            ExternalSubmissionRepository repository,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    void seed(ExternalSubmissionSeed seed, DemoOperationalSeedContext context) {
        List<ExternalSubmission> existing = find(seed.externalSubmissionId());
        if (!existing.isEmpty()) {
            verifyExisting(existing.get(0), seed, context);
            return;
        }
        Instant submittedAt = context.now().minus(seed.hoursAgo(), ChronoUnit.HOURS);
        repository.save(new ExternalSubmission(
                seed.externalSubmissionId(),
                seed.taskId(),
                context.companyId(),
                seed.destination(),
                seed.safeReference(),
                context.actorId(),
                submittedAt,
                submittedAt
        ));
        entityManager.flush();
    }

    void verifyExisting(ExternalSubmissionSeed seed, DemoOperationalSeedContext context) {
        List<ExternalSubmission> existing = find(seed.externalSubmissionId());
        if (existing.size() != 1) {
            throw new IllegalStateException("a reserved demo external submission was not seeded");
        }
        verifyExisting(existing.get(0), seed, context);
    }

    private List<ExternalSubmission> find(UUID submissionId) {
        return jdbcTemplate.query(
                "SELECT external_submission_id, task_id, company_id, destination, safe_reference, "
                        + "submitted_by, submitted_at, created_at FROM external_submission "
                        + "WHERE external_submission_id = ?",
                (resultSet, rowNumber) -> new ExternalSubmission(
                        resultSet.getObject("external_submission_id", UUID.class),
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        resultSet.getString("destination"),
                        resultSet.getString("safe_reference"),
                        resultSet.getObject("submitted_by", UUID.class),
                        resultSet.getTimestamp("submitted_at").toInstant(),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                submissionId
        );
    }

    private void verifyExisting(
            ExternalSubmission submission,
            ExternalSubmissionSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!seed.externalSubmissionId().equals(submission.externalSubmissionId())
                || !seed.taskId().equals(submission.taskId())
                || !context.companyId().equals(submission.companyId())
                || !seed.destination().equals(submission.destination())
                || !seed.safeReference().equals(submission.safeReference())
                || !context.actorId().equals(submission.submittedBy())
                || submission.createdAt().isBefore(submission.submittedAt())) {
            throw new IllegalStateException(
                    "a reserved demo external submission id belongs to different submission data"
            );
        }
    }
}
