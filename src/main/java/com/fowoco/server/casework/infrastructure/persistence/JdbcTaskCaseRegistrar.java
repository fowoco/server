package com.fowoco.server.casework.infrastructure.persistence;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskCaseRegistrar;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.time.LocalDate;
import java.util.ArrayList;
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
        List<UUID> existingWorkerIds = jdbcTemplate.query(
                "SELECT worker_id FROM workflow_case WHERE case_id = ? AND company_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("worker_id", UUID.class),
                task.caseId(),
                task.companyId()
        );
        if (!existingWorkerIds.isEmpty()) {
            if (!existingWorkerIds.get(0).equals(task.workerId())) {
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
                task.caseId(),
                task.companyId(),
                task.workerId(),
                task.title(),
                priority(task.dueDate(), today),
                task.workflowCatalogVersion(),
                snapshot(task, workflow),
                task.createdBy(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private String snapshot(Task task, WorkflowDefinition workflow) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("required_slots", sorted(workflow.requiredSlots()));
        conditions.put("completion_evidence", List.copyOf(workflow.completionEvidence()));

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("order", 1);
        step.put("task_id", task.taskId().toString());
        step.put("workflow_id", task.workflowId());
        step.put("task_type", task.taskType().name());
        step.put("required_conditions", conditions);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("workflow_catalog_version", task.workflowCatalogVersion());
        snapshot.put("steps", List.of(step));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("manual task workflow snapshot cannot be encoded", exception);
        }
    }

    private List<String> sorted(Iterable<String> values) {
        List<String> result = new ArrayList<>();
        values.forEach(result::add);
        return result.stream().sorted().toList();
    }

    private String priority(LocalDate dueDate, LocalDate today) {
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
