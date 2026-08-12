package com.fowoco.server.casework.infrastructure.persistence;

import com.fowoco.server.casework.application.CaseSearchQuery;
import com.fowoco.server.casework.application.port.CaseQueryRepository;
import com.fowoco.server.casework.domain.CaseLifecycleStatus;
import com.fowoco.server.casework.domain.CasePriority;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCaseQueryRepository implements CaseQueryRepository {

    private static final String CASE_SELECT = """
            SELECT c.case_id,
                   c.worker_id,
                   w.display_name AS worker_display_name,
                   c.title,
                   c.lifecycle_status,
                   c.priority,
                   c.workflow_catalog_version,
                   c.workflow_snapshot_json,
                   c.updated_at,
                   EXISTS (
                       SELECT 1
                         FROM task linked_task
                         JOIN worker_link link
                           ON link.task_id = linked_task.task_id
                          AND link.company_id = linked_task.company_id
                        WHERE linked_task.case_id = c.case_id
                          AND linked_task.company_id = c.company_id
                          AND link.status = 'ACTIVE'
                          AND link.expires_at > CURRENT_TIMESTAMP
                          AND link.delivery_status = 'SENT'
                   ) AS link_sent,
                   (
                       EXISTS (
                           SELECT 1 FROM task review_task
                            WHERE review_task.case_id = c.case_id
                              AND review_task.company_id = c.company_id
                              AND review_task.status = 'READY_FOR_REVIEW'
                       )
                       OR EXISTS (
                           SELECT 1
                             FROM task approval_task
                             JOIN approval_request approval
                               ON approval.task_id = approval_task.task_id
                              AND approval.company_id = approval_task.company_id
                            WHERE approval_task.case_id = c.case_id
                              AND approval_task.company_id = c.company_id
                              AND approval.status = 'PENDING'
                       )
                       OR EXISTS (
                           SELECT 1
                             FROM task response_task
                             JOIN worker_link response_link
                               ON response_link.task_id = response_task.task_id
                              AND response_link.company_id = response_task.company_id
                            WHERE response_task.case_id = c.case_id
                              AND response_task.company_id = c.company_id
                              AND response_link.conversation_status IN ('NEEDS_FOLLOWUP', 'REOPENED')
                       )
                   ) AS review_required,
                   EXISTS (
                       SELECT 1
                         FROM task unread_task
                         JOIN worker_link unread_link
                           ON unread_link.task_id = unread_task.task_id
                          AND unread_link.company_id = unread_task.company_id
                         JOIN worker_response response
                           ON response.worker_link_id = unread_link.worker_link_id
                          AND response.company_id = unread_link.company_id
                        WHERE unread_task.case_id = c.case_id
                          AND unread_task.company_id = c.company_id
                          AND unread_link.conversation_status = 'NEEDS_FOLLOWUP'
                   ) AS unread_response
                   ,(
                       SELECT COUNT(*)
                         FROM task checklist_task
                         JOIN task_checklist_item checklist
                           ON checklist.task_id = checklist_task.task_id
                          AND checklist.company_id = checklist_task.company_id
                        WHERE checklist_task.case_id = c.case_id
                          AND checklist_task.company_id = c.company_id
                          AND checklist.completed = TRUE
                   ) AS completed_checklist_items
                   ,(
                       SELECT COUNT(*)
                         FROM task checklist_task
                         JOIN task_checklist_item checklist
                           ON checklist.task_id = checklist_task.task_id
                          AND checklist.company_id = checklist_task.company_id
                        WHERE checklist_task.case_id = c.case_id
                          AND checklist_task.company_id = c.company_id
                   ) AS total_checklist_items
                   ,(
                       SELECT COUNT(*)
                         FROM task document_task
                         JOIN worker_document document
                           ON document.task_id = document_task.task_id
                          AND document.company_id = document_task.company_id
                        WHERE document_task.case_id = c.case_id
                          AND document_task.company_id = c.company_id
                          AND document.submission_status = 'VERIFIED'
                   ) AS verified_documents
                   ,(
                       SELECT COUNT(*)
                         FROM task document_task
                         JOIN worker_document document
                           ON document.task_id = document_task.task_id
                          AND document.company_id = document_task.company_id
                        WHERE document_task.case_id = c.case_id
                          AND document_task.company_id = c.company_id
                   ) AS total_documents
                   ,(
                       SELECT COUNT(*)
                         FROM task approval_task
                         JOIN approval_request approval
                           ON approval.task_id = approval_task.task_id
                          AND approval.company_id = approval_task.company_id
                        WHERE approval_task.case_id = c.case_id
                          AND approval_task.company_id = c.company_id
                          AND approval.status = 'PENDING'
                   ) AS pending_approvals
                   ,(
                       SELECT COUNT(*)
                         FROM task approval_task
                         JOIN approval_request approval
                           ON approval.task_id = approval_task.task_id
                          AND approval.company_id = approval_task.company_id
                        WHERE approval_task.case_id = c.case_id
                          AND approval_task.company_id = c.company_id
                          AND approval.status = 'APPROVED'
                   ) AS approved_approvals
                   ,(
                       SELECT COUNT(*)
                         FROM task response_task
                         JOIN worker_link response_link
                           ON response_link.task_id = response_task.task_id
                          AND response_link.company_id = response_task.company_id
                         JOIN worker_response response
                           ON response.worker_link_id = response_link.worker_link_id
                          AND response.company_id = response_link.company_id
                        WHERE response_task.case_id = c.case_id
                          AND response_task.company_id = c.company_id
                   ) AS worker_responses
                   ,(
                       SELECT COUNT(*)
                         FROM task evidence_task
                         JOIN task_evidence evidence
                           ON evidence.task_id = evidence_task.task_id
                          AND evidence.company_id = evidence_task.company_id
                        WHERE evidence_task.case_id = c.case_id
                          AND evidence_task.company_id = c.company_id
                   ) AS evidence_items
              FROM workflow_case c
              JOIN worker w
                ON w.worker_id = c.worker_id
               AND w.company_id = c.company_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCaseQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CaseRecordPage findPage(UUID companyId, CaseSearchQuery query) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("companyId", companyId);
        parameters.put("limit", query.size());
        parameters.put("offset", query.page() * query.size());
        String filter = " WHERE c.company_id = :companyId";
        if (query.keyword() != null) {
            filter += " AND (LOWER(c.title) LIKE :keyword OR LOWER(w.display_name) LIKE :keyword)";
            parameters.put("keyword", "%" + query.keyword().toLowerCase() + "%");
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_case c JOIN worker w"
                        + " ON w.worker_id = c.worker_id AND w.company_id = c.company_id"
                        + filter,
                parameters,
                Long.class
        );
        List<CaseRecord> items = jdbcTemplate.query(
                CASE_SELECT + filter
                        + " ORDER BY CASE c.priority"
                        + " WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1"
                        + " WHEN 'NORMAL' THEN 2 ELSE 3 END, c.updated_at DESC"
                        + ", c.case_id ASC"
                        + " LIMIT :limit OFFSET :offset",
                parameters,
                (resultSet, rowNumber) -> mapCase(resultSet)
        );
        return new CaseRecordPage(items, total == null ? 0 : total);
    }

    @Override
    public Optional<CaseRecord> findById(UUID companyId, UUID caseId) {
        Map<String, Object> parameters = Map.of("companyId", companyId, "caseId", caseId);
        List<CaseRecord> rows = jdbcTemplate.query(
                CASE_SELECT + " WHERE c.company_id = :companyId AND c.case_id = :caseId",
                parameters,
                (resultSet, rowNumber) -> mapCase(resultSet)
        );
        return rows.stream().findFirst();
    }

    @Override
    public List<CaseTaskRecord> findTasks(UUID companyId, List<UUID> caseIds) {
        if (caseIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT task.case_id,
                       task.task_id,
                       task.task_type,
                       task.title,
                       task.status,
                       task.due_date,
                       COALESCE(task.assignee_id, task.created_by) AS assignee_id,
                       assignee.display_name AS assignee_display_name
                  FROM task
                  JOIN user_account assignee
                    ON assignee.user_id = COALESCE(task.assignee_id, task.created_by)
                   AND assignee.company_id = task.company_id
                 WHERE task.company_id = :companyId
                   AND task.case_id IN (:caseIds)
                 ORDER BY task.due_date ASC, task.created_at ASC
                """,
                Map.of("companyId", companyId, "caseIds", caseIds),
                (resultSet, rowNumber) -> new CaseTaskRecord(
                        resultSet.getObject("case_id", UUID.class),
                        resultSet.getObject("task_id", UUID.class),
                        TaskType.valueOf(resultSet.getString("task_type")),
                        resultSet.getString("title"),
                        TaskStatus.valueOf(resultSet.getString("status")),
                        resultSet.getObject("due_date", java.time.LocalDate.class),
                        resultSet.getObject("assignee_id", UUID.class),
                        resultSet.getString("assignee_display_name")
                )
        );
    }

    private CaseRecord mapCase(ResultSet resultSet) throws SQLException {
        return new CaseRecord(
                resultSet.getObject("case_id", UUID.class),
                resultSet.getObject("worker_id", UUID.class),
                resultSet.getString("worker_display_name"),
                resultSet.getString("title"),
                CaseLifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                CasePriority.valueOf(resultSet.getString("priority")),
                resultSet.getString("workflow_catalog_version"),
                resultSet.getString("workflow_snapshot_json"),
                resultSet.getBoolean("link_sent"),
                resultSet.getBoolean("review_required"),
                resultSet.getBoolean("unread_response"),
                resultSet.getInt("completed_checklist_items"),
                resultSet.getInt("total_checklist_items"),
                resultSet.getInt("verified_documents"),
                resultSet.getInt("total_documents"),
                resultSet.getInt("pending_approvals"),
                resultSet.getInt("approved_approvals"),
                resultSet.getInt("worker_responses"),
                resultSet.getInt("evidence_items"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
