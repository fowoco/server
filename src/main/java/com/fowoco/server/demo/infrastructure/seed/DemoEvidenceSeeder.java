package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.approval.application.port.EvidenceRepository;
import com.fowoco.server.approval.domain.Evidence;
import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.EvidenceSeed;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class DemoEvidenceSeeder {

    private final EvidenceRepository repository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    DemoEvidenceSeeder(
            EvidenceRepository repository,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    void seed(EvidenceSeed seed, DemoOperationalSeedContext context) {
        List<Evidence> existing = find(seed.evidenceId());
        if (!existing.isEmpty()) {
            verifyExisting(existing.get(0), seed, context);
            return;
        }
        Instant recordedAt = context.now().minus(seed.hoursAgo(), ChronoUnit.HOURS);
        repository.save(new Evidence(
                seed.evidenceId(),
                seed.taskId(),
                context.companyId(),
                seed.evidenceType(),
                null,
                seed.note(),
                context.actorId(),
                recordedAt,
                recordedAt
        ));
        entityManager.flush();
    }

    void verifyExisting(EvidenceSeed seed, DemoOperationalSeedContext context) {
        List<Evidence> existing = find(seed.evidenceId());
        if (existing.size() != 1) {
            throw new IllegalStateException("a reserved demo completion evidence was not seeded");
        }
        verifyExisting(existing.get(0), seed, context);
    }

    private List<Evidence> find(UUID evidenceId) {
        return jdbcTemplate.query(
                "SELECT evidence_id, task_id, company_id, evidence_type, file_reference, note, "
                        + "recorded_by, recorded_at, created_at FROM task_evidence WHERE evidence_id = ?",
                (resultSet, rowNumber) -> new Evidence(
                        resultSet.getObject("evidence_id", UUID.class),
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        EvidenceType.valueOf(resultSet.getString("evidence_type")),
                        resultSet.getString("file_reference"),
                        resultSet.getString("note"),
                        resultSet.getObject("recorded_by", UUID.class),
                        resultSet.getTimestamp("recorded_at").toInstant(),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                evidenceId
        );
    }

    private void verifyExisting(
            Evidence evidence,
            EvidenceSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!seed.evidenceId().equals(evidence.evidenceId())
                || !seed.taskId().equals(evidence.taskId())
                || !context.companyId().equals(evidence.companyId())
                || seed.evidenceType() != evidence.evidenceType()
                || evidence.fileReference() != null
                || !seed.note().equals(evidence.note())
                || !context.actorId().equals(evidence.recordedBy())
                || evidence.createdAt().isBefore(evidence.recordedAt())) {
            throw new IllegalStateException(
                    "a reserved demo evidence id already belongs to different evidence data"
            );
        }
    }
}
