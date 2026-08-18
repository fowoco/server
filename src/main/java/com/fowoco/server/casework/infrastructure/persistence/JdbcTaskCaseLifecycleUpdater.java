package com.fowoco.server.casework.infrastructure.persistence;

import com.fowoco.server.task.application.port.TaskCaseLifecycleUpdater;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTaskCaseLifecycleUpdater implements TaskCaseLifecycleUpdater {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskCaseLifecycleUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean completeIfAllTasksFinished(UUID caseId, UUID companyId, Instant completedAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE workflow_case workflow_case
                   SET lifecycle_status = 'COMPLETED',
                       updated_at = ?,
                       version = version + 1
                 WHERE workflow_case.case_id = ?
                   AND workflow_case.company_id = ?
                   AND workflow_case.lifecycle_status = 'ACTIVE'
                   AND EXISTS (
                       SELECT 1
                         FROM task completed_task
                        WHERE completed_task.case_id = workflow_case.case_id
                          AND completed_task.company_id = workflow_case.company_id
                          AND completed_task.status = 'COMPLETED'
                   )
                   AND NOT EXISTS (
                       SELECT 1
                         FROM task unfinished_task
                        WHERE unfinished_task.case_id = workflow_case.case_id
                          AND unfinished_task.company_id = workflow_case.company_id
                          AND unfinished_task.status NOT IN ('COMPLETED', 'CANCELLED')
                   )
                """,
                Timestamp.from(completedAt),
                caseId,
                companyId
        );
        return updated == 1;
    }

    @Override
    public boolean cancelIfAllTasksCancelled(UUID caseId, UUID companyId, Instant cancelledAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE workflow_case workflow_case
                   SET lifecycle_status = 'CANCELLED',
                       updated_at = ?,
                       version = version + 1
                 WHERE workflow_case.case_id = ?
                   AND workflow_case.company_id = ?
                   AND workflow_case.lifecycle_status = 'ACTIVE'
                   AND EXISTS (
                       SELECT 1
                         FROM task cancelled_task
                        WHERE cancelled_task.case_id = workflow_case.case_id
                          AND cancelled_task.company_id = workflow_case.company_id
                          AND cancelled_task.status = 'CANCELLED'
                   )
                   AND NOT EXISTS (
                       SELECT 1
                         FROM task remaining_task
                        WHERE remaining_task.case_id = workflow_case.case_id
                          AND remaining_task.company_id = workflow_case.company_id
                          AND remaining_task.status <> 'CANCELLED'
                   )
                """,
                Timestamp.from(cancelledAt),
                caseId,
                companyId
        );
        return updated == 1;
    }
}
