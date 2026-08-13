package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TransitionSeed;
import com.fowoco.server.task.domain.TaskStatus;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class DemoTaskTransitionSeeder {

    private static final Set<UUID> APPROVAL_BACKFILL_TRANSITION_IDS = Set.of(
            UUID.fromString("94400000-0000-0000-0000-000000000006"),
            UUID.fromString("94400000-0000-0000-0000-000000000022"),
            UUID.fromString("94400000-0000-0000-0000-000000000025")
    );

    private final JdbcTemplate jdbcTemplate;

    DemoTaskTransitionSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    void seed(TransitionSeed seed, DemoOperationalSeedContext context) {
        List<StoredTransition> existing = find(seed.transitionId());
        if (!existing.isEmpty()) {
            if (upgradeLegacyApprovalBypass(existing.get(0), seed, context)) {
                return;
            }
            verifyExisting(existing.get(0), seed, context);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO task_transition_history "
                        + "(transition_id, task_id, company_id, from_status, to_status, actor_id, "
                        + "reason, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                seed.transitionId(),
                seed.taskId(),
                context.companyId(),
                seed.fromStatus().name(),
                seed.toStatus().name(),
                context.actorId(),
                seed.reason(),
                seed.requestId(),
                Timestamp.from(context.now().minus(seed.hoursAgo(), ChronoUnit.HOURS))
        );
    }

    private boolean upgradeLegacyApprovalBypass(
            StoredTransition transition,
            TransitionSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!APPROVAL_BACKFILL_TRANSITION_IDS.contains(seed.transitionId())
                || transition.fromStatus() != TaskStatus.DRAFT
                || seed.fromStatus() != TaskStatus.APPROVED
                || transition.toStatus() != TaskStatus.WAITING_WORKER
                || seed.toStatus() != TaskStatus.WAITING_WORKER
                || !seed.taskId().equals(transition.taskId())
                || !context.companyId().equals(transition.companyId())
                || !context.actorId().equals(transition.actorId())
                || !seed.reason().equals(transition.reason())
                || !seed.requestId().equals(transition.requestId())) {
            return false;
        }
        int updated = jdbcTemplate.update(
                "UPDATE task_transition_history SET from_status = ? "
                        + "WHERE transition_id = ? AND task_id = ? AND company_id = ? "
                        + "AND from_status = ? AND to_status = ? AND actor_id = ? "
                        + "AND reason = ? AND request_id = ?",
                TaskStatus.APPROVED.name(),
                seed.transitionId(),
                seed.taskId(),
                context.companyId(),
                TaskStatus.DRAFT.name(),
                TaskStatus.WAITING_WORKER.name(),
                context.actorId(),
                seed.reason(),
                seed.requestId()
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "legacy demo task transition approval backfill was not applied"
            );
        }
        return true;
    }

    void verifyExisting(TransitionSeed seed, DemoOperationalSeedContext context) {
        List<StoredTransition> existing = find(seed.transitionId());
        if (existing.size() != 1) {
            throw new IllegalStateException("a reserved demo task transition was not seeded");
        }
        verifyExisting(existing.get(0), seed, context);
    }

    private List<StoredTransition> find(UUID transitionId) {
        return jdbcTemplate.query(
                "SELECT task_id, company_id, from_status, to_status, actor_id, reason, request_id "
                        + "FROM task_transition_history WHERE transition_id = ?",
                (resultSet, rowNumber) -> new StoredTransition(
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        TaskStatus.valueOf(resultSet.getString("from_status")),
                        TaskStatus.valueOf(resultSet.getString("to_status")),
                        resultSet.getObject("actor_id", UUID.class),
                        resultSet.getString("reason"),
                        resultSet.getString("request_id")
                ),
                transitionId
        );
    }

    private void verifyExisting(
            StoredTransition transition,
            TransitionSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!seed.taskId().equals(transition.taskId())
                || !context.companyId().equals(transition.companyId())
                || seed.fromStatus() != transition.fromStatus()
                || seed.toStatus() != transition.toStatus()
                || !context.actorId().equals(transition.actorId())
                || !seed.reason().equals(transition.reason())
                || !seed.requestId().equals(transition.requestId())) {
            throw new IllegalStateException(
                    "a reserved demo task transition id already belongs to different transition data"
            );
        }
    }

    private record StoredTransition(
            UUID taskId,
            UUID companyId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            UUID actorId,
            String reason,
            String requestId
    ) {
    }
}
