package com.fowoco.server.auth.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.demo-seed.enabled=true",
        "app.demo-seed.admin-password=Demo-password-1!"
})
class DemoAuthSeedIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TEST_COMPANY_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    @Qualifier("demoOperationalSeedRunner")
    private ApplicationRunner demoOperationalSeedRunner;

    @Test
    void enabledSeedCreatesIdempotentCompanyAccountWorkerAndOperationalDataAfterFlyway()
            throws Exception {
        demoOperationalSeedRunner.run(new DefaultApplicationArguments(new String[0]));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE user_id = ? AND company_id = ?",
                String.class,
                ADMIN_USER_ID,
                COMPANY_ID
        );

        assertThat(passwordHash).isNotEqualTo("Demo-password-1!");
        assertThat(passwordEncoder.matches("Demo-password-1!", passwordHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE company_id = ? AND status = 'ACTIVE'",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE company_id = ? "
                        + "AND name = 'FOWOCO Test Company' AND status = 'ACTIVE'",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE user_id = ? AND display_name = '데모 관리자' "
                        + "AND role = 'ADMIN' AND status = 'ACTIVE'",
                Integer.class,
                ADMIN_USER_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ?",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(20);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ? AND role = 'ADMIN'",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ? AND role = 'HR'",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ? AND role = 'VIEWER'",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ?",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ? AND role = 'ADMIN'",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ? AND role = 'HR'",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE company_id = ? AND role = 'VIEWER'",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE company_id = ?",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(28);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE company_id = ?",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE company_id = ?",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(24);
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM task WHERE company_id = ?",
                String.class,
                COMPANY_ID
        )).contains(
                "DRAFT",
                "NEEDS_INFO",
                "READY_FOR_REVIEW",
                "APPROVED",
                "WAITING_WORKER",
                "WAITING_EXTERNAL",
                "COMPLETED",
                "CANCELLED"
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE company_id = ? AND source = 'AI_CANDIDATE' "
                        + "AND status = 'DRAFT'",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(3);
        List<Instant> completedUpdates = jdbcTemplate.query(
                "SELECT updated_at FROM task WHERE company_id = ? AND status = 'COMPLETED'",
                (resultSet, rowNumber) -> resultSet.getTimestamp("updated_at").toInstant(),
                COMPANY_ID
        );
        assertThat(completedUpdates).hasSize(5).allMatch(updatedAt ->
                LocalDate.ofInstant(updatedAt, ZoneOffset.UTC).equals(LocalDate.now(clock)));
        List<LocalDate> dueDates = jdbcTemplate.query(
                "SELECT due_date FROM task WHERE company_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("due_date", LocalDate.class),
                COMPANY_ID
        );
        LocalDate today = LocalDate.now(clock);
        assertThat(dueDates).anyMatch(date -> !date.isAfter(today.plusDays(7)));
        assertThat(dueDates).anyMatch(date ->
                date.isAfter(today.plusDays(7)) && !date.isAfter(today.plusDays(30)));
        assertThat(dueDates).anyMatch(date -> date.isAfter(today.plusDays(30)));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ?",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(84);
        assertThat(jdbcTemplate.queryForList(
                "SELECT submission_status FROM worker_document WHERE company_id = ?",
                String.class,
                COMPANY_ID
        )).contains("MISSING", "SUBMITTED", "VERIFIED");
        List<LocalDate> expiryDates = jdbcTemplate.query(
                "SELECT expiry_date FROM worker_document WHERE company_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("expiry_date", LocalDate.class),
                COMPANY_ID
        );
        assertThat(expiryDates).anyMatch(date ->
                date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(30)));
        assertThat(jdbcTemplate.queryForList(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? GROUP BY worker_id",
                Integer.class,
                COMPANY_ID
        )).hasSize(28).allMatch(count -> count == 3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE company_id = ?",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ?",
                Integer.class,
                TEST_COMPANY_ID
        )).isEqualTo(8);
        UUID timelineTaskId = UUID.fromString("94000000-0000-0000-0000-000000000002");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE company_id = ? AND target_id = ?",
                Integer.class,
                COMPANY_ID,
                timelineTaskId
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                "SELECT action FROM audit_event WHERE company_id = ? AND target_id = ?",
                String.class,
                COMPANY_ID,
                timelineTaskId
        )).containsExactlyInAnyOrder("TASK_CREATED", "TASK_UPDATED", "APPROVAL_REQUESTED");
    }
}
