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

    private String snapshot(List<TaskSeed> tasks) {
        List<Map<String, String>> workflows = tasks.stream()
                .sorted(Comparator.comparing(TaskSeed::taskId))
                .map(task -> Map.of(
                        "workflow_id", task.workflowId(),
                        "task_type", task.taskType().name()
                ))
                .toList();
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "workflow_catalog_version", DemoOperationalSeedCatalog.WORKFLOW_CATALOG_VERSION,
                    "workflows", workflows
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("demo workflow snapshot cannot be encoded", exception);
        }
    }
}
