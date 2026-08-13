package com.fowoco.server.audit.infrastructure.persistence;

import com.fowoco.server.audit.application.AuditSearchCriteria;
import com.fowoco.server.audit.application.WorkerActivitySearchCriteria;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.application.port.AuditEventRepository.WorkerActivityRecord;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditEventRepository implements AuditEventRepository {

    private final SpringDataAuditEventJpaRepository repository;
    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JpaAuditEventRepository(
            SpringDataAuditEventJpaRepository repository,
            EntityManager entityManager,
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(AuditEvent event) {
        repository.save(new AuditEventJpaEntity(event));
    }

    @Override
    public Optional<AuditEvent> findById(UUID auditEventId) {
        return repository.findById(auditEventId).map(AuditEventJpaEntity::toDomain);
    }

    @Override
    public List<AuditEvent> findTaskActivities(UUID companyId, UUID taskId) {
        return repository
                .findTop200ByCompanyIdAndTargetTypeAndTargetIdOrderByCreatedAtAscAuditEventIdAsc(
                        companyId,
                        AuditTargetType.TASK,
                        taskId
                )
                .stream()
                .map(AuditEventJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<AuditEvent> search(AuditSearchCriteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditEventJpaEntity> query = builder.createQuery(AuditEventJpaEntity.class);
        Root<AuditEventJpaEntity> event = query.from(AuditEventJpaEntity.class);
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(event.get("companyId"), criteria.companyId()));
        if (criteria.actorType() != null) {
            predicates.add(builder.equal(event.get("actorType"), criteria.actorType()));
        }
        if (criteria.action() != null) {
            predicates.add(builder.equal(event.get("action"), criteria.action()));
        }
        if (criteria.targetType() != null) {
            predicates.add(builder.equal(event.get("targetType"), criteria.targetType()));
        }
        if (criteria.targetId() != null) {
            predicates.add(builder.equal(event.get("targetId"), criteria.targetId()));
        }
        if (criteria.traceId() != null) {
            predicates.add(builder.equal(event.get("traceId"), criteria.traceId()));
        }

        var createdAt = event.<Instant>get("createdAt");
        if (criteria.createdFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(createdAt, criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            predicates.add(builder.lessThanOrEqualTo(createdAt, criteria.createdTo()));
        }
        if (criteria.beforeCreatedAt() != null) {
            predicates.add(builder.or(
                    builder.lessThan(createdAt, criteria.beforeCreatedAt()),
                    builder.and(
                            builder.equal(createdAt, criteria.beforeCreatedAt()),
                            builder.lessThan(
                                    event.<UUID>get("auditEventId"),
                                    criteria.beforeAuditEventId()
                            )
                    )
            ));
        }

        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(
                builder.desc(createdAt),
                builder.desc(event.get("auditEventId"))
        );

        return entityManager.createQuery(query)
                .setMaxResults(criteria.limit())
                .getResultList()
                .stream()
                .map(AuditEventJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<WorkerActivityRecord> findWorkerActivities(
            WorkerActivitySearchCriteria criteria
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT audit.audit_event_id,
                       audit.action,
                       task.task_id,
                       task.title AS task_title,
                       audit.created_at
                  FROM audit_event audit
                  JOIN task
                    ON task.company_id = audit.company_id
                   AND task.worker_id = :workerId
                   AND (
                        (audit.target_type = 'TASK' AND audit.target_id = task.task_id)
                        OR
                        (audit.target_type = 'WORKER_LINK' AND EXISTS (
                            SELECT 1
                              FROM worker_link link
                             WHERE link.company_id = audit.company_id
                               AND link.task_id = task.task_id
                               AND link.worker_link_id = audit.target_id
                        ))
                   )
                 WHERE audit.company_id = :companyId
                   AND audit.action IN (:actions)
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("companyId", criteria.companyId())
                .addValue("workerId", criteria.workerId())
                .addValue(
                        "actions",
                        criteria.actions().stream().map(Enum::name).toList()
                );
        if (criteria.beforeCreatedAt() != null) {
            sql.append("""
                       AND (
                            audit.created_at < :beforeCreatedAt
                            OR (
                                audit.created_at = :beforeCreatedAt
                                AND audit.audit_event_id < :beforeAuditEventId
                            )
                       )
                    """);
            parameters
                    .addValue("beforeCreatedAt", criteria.beforeCreatedAt())
                    .addValue("beforeAuditEventId", criteria.beforeAuditEventId());
        }
        sql.append("""
                 ORDER BY audit.created_at DESC, audit.audit_event_id DESC
                 LIMIT :limit
                """);
        parameters.addValue("limit", criteria.limit());
        return jdbcTemplate.query(
                sql.toString(),
                parameters,
                (resultSet, rowNumber) -> new WorkerActivityRecord(
                        resultSet.getObject("audit_event_id", UUID.class),
                        com.fowoco.server.audit.domain.AuditAction.valueOf(
                                resultSet.getString("action")
                        ),
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getString("task_title"),
                        resultSet.getTimestamp("created_at").toInstant()
                )
        );
    }
}
