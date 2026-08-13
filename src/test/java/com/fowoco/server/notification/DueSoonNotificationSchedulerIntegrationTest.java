package com.fowoco.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.notification.application.DueSoonNotificationScheduler;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DueSoonNotificationSchedulerIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("56000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_A = UUID.fromString("57000000-0000-0000-0000-000000000001");
    private static final UUID CREATOR_ID = UUID.fromString("58000000-0000-0000-0000-000000000001");
    private static final UUID CASE_ID = UUID.fromString("59000000-0000-0000-0000-000000000001");
    @Autowired
    private DueSoonNotificationScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void seedCompanyAndWorker() {
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_A, "마감임박스케줄러테스트사업장"
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker (worker_id, company_id, display_name, work_status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
                WORKER_A, COMPANY_A, "테스트근로자"
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, 'x', 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                CREATOR_ID, COMPANY_A, "duesoon.creator@example.com", "duesoon.creator@example.com"
        );
    }

    @BeforeEach
    void resetTaskAndNotification() {
        jdbcTemplate.update("DELETE FROM notification");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
    }

    @Test
    void notifyDueSoonTasksRunsWithoutTransactionErrorAndCreatesNotification() {
        UUID taskId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id, company_id, target_type, worker_id, case_id, task_type,
                    workflow_id, workflow_catalog_version, title, description,
                    business_data_json, critical_fingerprint, source, status,
                    due_date, content_revision, version, created_by, updated_by,
                    created_at, updated_at
                ) VALUES (?, ?, 'WORKER', ?, ?, 'RECONTRACT', 'WF-CON-001', '0.2.0',
                    '마감임박테스트업무', '설명', '{}', ?, 'MANUAL', 'DRAFT',
                    ?, 0, 0, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                taskId, COMPANY_A, WORKER_A, CASE_ID, "a".repeat(64),
                java.sql.Date.valueOf(LocalDate.now().plusDays(3)),
                CREATOR_ID, CREATOR_ID
        );

        scheduler.notifyDueSoonTasks();

        Long notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE company_id = ?", Long.class, COMPANY_A
        );
        assertThat(notificationCount).isEqualTo(1L);

        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM notification WHERE company_id = ?", String.class, COMPANY_A
        );
        UUID notifiedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM notification WHERE company_id = ?", UUID.class, COMPANY_A
        );
        System.out.println("=== DueSoonNotificationScheduler 실제 생성 결과 ===");
        System.out.println("title: " + title);
        System.out.println("user_id (Task 생성자): " + notifiedUserId);
        System.out.println("expected creator_id : " + CREATOR_ID);
        assertThat(title).contains("마감이 임박했습니다");
        assertThat(notifiedUserId).isEqualTo(CREATOR_ID);
    }
}
