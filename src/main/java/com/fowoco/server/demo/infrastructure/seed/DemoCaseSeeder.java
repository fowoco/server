package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class DemoCaseSeeder {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    DemoCaseSeeder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    void seed(List<TaskSeed> tasks, DemoOperationalSeedContext context) {
        tasks.stream()
                .collect(Collectors.groupingBy(TaskSeed::caseId, LinkedHashMap::new, Collectors.toList()))
                .forEach((caseId, caseTasks) -> seedCase(caseId, caseTasks, context));
    }

    private void seedCase(UUID caseId, List<TaskSeed> tasks, DemoOperationalSeedContext context) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_case WHERE case_id = ? AND company_id = ?",
                Integer.class,
                caseId,
                context.companyId()
        );
        if (existing != null && existing > 0) {
            return;
        }
        TaskSeed first = tasks.get(0);
        UUID workerId = first.workerId();
        if (tasks.stream().anyMatch(task -> !workerId.equals(task.workerId()))) {
            throw new IllegalStateException("one demo case must belong to one worker");
        }
        Instant createdAt = context.now().minus(
                tasks.stream().mapToInt(TaskSeed::createdDaysAgo).max().orElse(0),
                ChronoUnit.DAYS
        );
        jdbcTemplate.update(
                """
                INSERT INTO workflow_case (
                    case_id, company_id, worker_id, title, lifecycle_status, priority,
                    workflow_catalog_version, workflow_snapshot_json, created_by,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                caseId,
                context.companyId(),
                workerId,
                title(tasks),
                lifecycleStatus(tasks),
                priority(tasks),
                DemoOperationalSeedCatalog.WORKFLOW_CATALOG_VERSION,
                snapshot(tasks),
                context.actorId(),
                createdAt,
                context.now()
        );
    }

    private String title(List<TaskSeed> tasks) {
        Object displayName = tasks.get(0).businessData().get("case_display_name");
        if (displayName instanceof String name && !name.isBlank()) {
            return name;
        }
        return tasks.size() == 1 ? tasks.get(0).title() : "통합 업무 준비";
    }

    private String lifecycleStatus(List<TaskSeed> tasks) {
        if (tasks.stream().allMatch(task -> task.status() == TaskStatus.COMPLETED)) {
            return "COMPLETED";
        }
        if (tasks.stream().allMatch(task -> task.status() == TaskStatus.CANCELLED)) {
            return "CANCELLED";
        }
        return "ACTIVE";
    }

    private String priority(List<TaskSeed> tasks) {
        int nearestDueDays = tasks.stream().mapToInt(TaskSeed::dueDays).min().orElse(30);
        if (nearestDueDays <= 3) {
            return "URGENT";
        }
        if (nearestDueDays <= 7) {
            return "HIGH";
        }
        if (nearestDueDays <= 30) {
            return "NORMAL";
        }
        return "LOW";
    }

    String snapshot(List<TaskSeed> tasks) {
        List<TaskSeed> orderedTasks = tasks.stream()
                .sorted(Comparator
                        .comparingInt((TaskSeed task) -> candidateOrder(task))
                        .thenComparing(TaskSeed::taskId))
                .toList();
        List<Map<String, Object>> steps = java.util.stream.IntStream
                .range(0, orderedTasks.size())
                .mapToObj(index -> snapshotStep(orderedTasks.get(index), index + 1))
                .toList();
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "workflow_catalog_version", DemoOperationalSeedCatalog.WORKFLOW_CATALOG_VERSION,
                    "steps", steps
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("demo workflow snapshot cannot be encoded", exception);
        }
    }

    private Map<String, Object> snapshotStep(TaskSeed task, int fallbackOrder) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("order", candidateOrder(task, fallbackOrder));
        step.put("task_id", task.taskId().toString());
        step.put("workflow_id", task.workflowId());
        step.put("task_type", task.taskType().name());
        step.put("required_conditions", requiredConditions(task.businessData()));
        return Map.copyOf(step);
    }

    private int candidateOrder(TaskSeed task) {
        return candidateOrder(task, Integer.MAX_VALUE);
    }

    private int candidateOrder(TaskSeed task, int fallback) {
        Object value = task.businessData().get("candidate_order");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return fallback;
    }

    private Map<String, Object> requiredConditions(Map<String, Object> businessData) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        List.of(
                "approval_required",
                "depends_on_task_id",
                "dependency_reason",
                "missing_information",
                "submission_due_offset_days"
        ).forEach(key -> {
            if (businessData.containsKey(key)) {
                conditions.put(key, businessData.get(key));
            }
        });
        return Map.copyOf(conditions);
    }
}
