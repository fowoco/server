package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.application.port.TaskChecklistStatusRepository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTaskChecklistStatusRepository implements TaskChecklistStatusRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTaskChecklistStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsIncompleteRequiredItem(UUID taskId, UUID companyId) {
        Boolean result = jdbcTemplate.queryForObject(
                """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                      FROM task_checklist_item
                     WHERE task_id = ?
                       AND company_id = ?
                       AND required = TRUE
                       AND completed = FALSE
                ) THEN TRUE ELSE FALSE END
                """,
                Boolean.class,
                taskId,
                companyId
        );
        return Boolean.TRUE.equals(result);
    }
}
