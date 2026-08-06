package com.fowoco.server.casework.infrastructure.persistence;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskCaseRegistrar;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcTaskCaseRegistrar implements TaskCaseRegistrar {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcTaskCaseRegistrar(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void register(Task task, WorkflowDefinition workflow, LocalDate today) {
        registerComposite(List.of(new CaseTask(task, workflow)), today);
    }

    @Override
    public void registerComposite(List<CaseTask> caseTasks, LocalDate today) {
        if (caseTasks == null || caseTasks.isEmpty()) {
            throw new IllegalArgumentException("caseTasks must not be empty");
        }
        List<CaseTask> orderedTasks = caseTasks.stream()
                .sorted(Comparator
                        .comparingInt((CaseTask caseTask) -> candidateOrder(caseTask.task()))
                        .thenComparing(caseTask -> caseTask.task().taskId()))
                .toList();
        Task first = orderedTasks.get(0).task();
        boolean mixedScope = orderedTasks.stream().anyMatch(caseTask -> {
            Task task = caseTask.task();
            return !first.caseId().equals(task.caseId())
                    || !first.companyId().equals(task.companyId())
                    || !first.workerId().equals(task.workerId());
        });
        if (mixedScope) {
            throw new ApiException(TaskErrorCode.CASE_WORKER_MISMATCH);
        }
        List<UUID> existingWorkerIds = jdbcTemplate.query(
                "SELECT worker_id FROM workflow_case WHERE case_id = ? AND company_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("worker_id", UUID.class),
                first.caseId(),
                first.companyId()
        );
        if (!existingWorkerIds.isEmpty()) {
            if (!existingWorkerIds.get(0).equals(first.workerId())) {
                throw new ApiException(TaskErrorCode.CASE_WORKER_MISMATCH);
            }
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO workflow_case (
                    case_id, company_id, worker_id, title, lifecycle_status, priority,
                    workflow_catalog_version, workflow_snapshot_json, created_by,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, 0)
                """,
                first.caseId(),
                first.companyId(),
                first.workerId(),
                orderedTasks.size() == 1 ? first.title() : "3년 만료 연장 준비",
                priority(orderedTasks, today),
                first.workflowCatalogVersion(),
                snapshot(orderedTasks),
                first.createdBy(),
                first.createdAt(),
                first.updatedAt()
        );
    }

    private String snapshot(List<CaseTask> caseTasks) {
        List<Map<String, Object>> steps = java.util.stream.IntStream
                .range(0, caseTasks.size())
                .mapToObj(index -> snapshotStep(caseTasks.get(index), index + 1))
                .toList();
        Task first = caseTasks.get(0).task();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("workflow_catalog_version", first.workflowCatalogVersion());
        snapshot.put("steps", steps);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("task workflow snapshot cannot be encoded", exception);
        }
    }

    private Map<String, Object> snapshotStep(CaseTask caseTask, int fallbackOrder) {
        Task task = caseTask.task();
        WorkflowDefinition workflow = caseTask.workflow();
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("required_slots", sorted(workflow.requiredSlots()));
        conditions.put("completion_evidence", List.copyOf(workflow.completionEvidence()));
        Map<String, Object> businessData = businessData(task);
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

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("order", candidateOrder(task, fallbackOrder));
        step.put("task_id", task.taskId().toString());
        step.put("workflow_id", task.workflowId());
        step.put("task_type", task.taskType().name());
        step.put("required_conditions", conditions);
        return Map.copyOf(step);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> businessData(Task task) {
        try {
            return objectMapper.readValue(task.businessDataJson(), Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("task business data cannot be decoded", exception);
        }
    }

    private int candidateOrder(Task task) {
        return candidateOrder(task, Integer.MAX_VALUE);
    }

    private int candidateOrder(Task task, int fallback) {
        Object value = businessData(task).get("candidate_order");
        return value instanceof Number number && number.intValue() > 0
                ? number.intValue()
                : fallback;
    }

    private List<String> sorted(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        values.forEach(result::add);
        return result.stream().sorted().toList();
    }

    private String priority(List<CaseTask> caseTasks, LocalDate today) {
        LocalDate dueDate = caseTasks.stream()
                .map(caseTask -> caseTask.task().dueDate())
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (dueDate == null) {
            return "NORMAL";
        }
        long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
        if (remainingDays <= 3) {
            return "URGENT";
        }
        if (remainingDays <= 7) {
            return "HIGH";
        }
        if (remainingDays <= 30) {
            return "NORMAL";
        }
        return "LOW";
    }
}
