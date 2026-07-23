package com.fowoco.server.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fowoco.server.approval.application.ApprovalService;
import com.fowoco.server.approval.application.RequestApprovalCommand;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.web.RequestMetadata;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class ApprovalAuditRollbackIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_ID =
            UUID.fromString("c2000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID =
            UUID.fromString("c3000000-0000-0000-0000-000000000001");

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuditEventRepository auditRepository;

    @BeforeEach
    void seedTask() {
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        jdbcTemplate.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, '감사 롤백 테스트', 'ACTIVE',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, 'rollback@example.com', 'rollback@example.com',
                          'not-used-password-hash', 'HR', 'ACTIVE',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                ACTOR_ID,
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality, preferred_language,
                    employment_status, stay_expiry_date, created_at, updated_at, version
                ) VALUES (?, ?, '근로자', 'VNM', 'vi', 'ACTIVE', ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                WORKER_ID,
                COMPANY_ID,
                LocalDate.of(2027, 8, 31)
        );
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id, company_id, worker_id, case_id, task_type,
                    workflow_id, workflow_catalog_version, title,
                    business_data_json, critical_fingerprint, content_revision,
                    source, status, created_by, updated_by,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'RECONTRACT',
                          'e9-recontract', '2026.07', '재계약',
                          '{}', ?, 0, 'MANUAL', 'DRAFT', ?, ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                TASK_ID,
                COMPANY_ID,
                WORKER_ID,
                UUID.fromString("c4000000-0000-0000-0000-000000000001"),
                "a".repeat(64),
                ACTOR_ID,
                ACTOR_ID
        );
    }

    @Test
    void auditAppendFailureRollsBackTaskApprovalRequestAndTransition() {
        doThrow(new IllegalStateException("simulated audit storage failure"))
                .when(auditRepository)
                .append(any());

        assertThatThrownBy(() -> approvalService.requestApproval(
                TASK_ID,
                new RequestApprovalCommand(
                        0,
                        null,
                        Map.of("monthly_wage", 2_500_000),
                        List.of("monthly_wage"),
                        Map.of("workflow_catalog_version", "2026.07")
                ),
                new ActorContext(ACTOR_ID, COMPANY_ID, Set.of(UserRole.HR)),
                new RequestMetadata("rollback-request", null)
        )).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE task_id = ?",
                String.class,
                TASK_ID
        )).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM task WHERE task_id = ?",
                Long.class,
                TASK_ID
        )).isZero();
        assertThat(count("approval_request")).isZero();
        assertThat(count("task_transition_history")).isZero();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
