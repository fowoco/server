package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TransitionSeed;
import com.fowoco.server.task.domain.TaskStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DemoTaskTransitionSeederTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000003");
    private static final UUID TRANSITION_ID =
            UUID.fromString("94400000-0000-0000-0000-000000000006");
    private static final String REASON = "근로자 제출 자료 대기";
    private static final String REQUEST_ID = "demo-seed-task-transition-006";

    private JdbcTemplate jdbcTemplate;
    private DemoTaskTransitionSeeder seeder;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:demo-transition-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(
                "CREATE TABLE task_transition_history ("
                        + "transition_id UUID PRIMARY KEY, task_id UUID NOT NULL, "
                        + "company_id UUID NOT NULL, from_status VARCHAR(40) NOT NULL, "
                        + "to_status VARCHAR(40) NOT NULL, actor_id UUID NOT NULL, "
                        + "reason VARCHAR(255) NOT NULL, request_id VARCHAR(255) NOT NULL, "
                        + "created_at TIMESTAMP NOT NULL)"
        );
        seeder = new DemoTaskTransitionSeeder(jdbcTemplate);
    }

    @Test
    void upgradesPreviousReleaseApprovalBypassWithoutChangingItsReservedId() {
        jdbcTemplate.update(
                "INSERT INTO task_transition_history "
                        + "(transition_id, task_id, company_id, from_status, to_status, actor_id, "
                        + "reason, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                TRANSITION_ID,
                TASK_ID,
                COMPANY_ID,
                TaskStatus.DRAFT.name(),
                TaskStatus.WAITING_WORKER.name(),
                ACTOR_ID,
                REASON,
                REQUEST_ID,
                Timestamp.from(Instant.parse("2026-08-01T00:00:00Z"))
        );
        TransitionSeed currentSeed = new TransitionSeed(
                TRANSITION_ID,
                TASK_ID,
                TaskStatus.APPROVED,
                TaskStatus.WAITING_WORKER,
                REASON,
                REQUEST_ID,
                1
        );
        DemoOperationalSeedContext context = new DemoOperationalSeedContext(
                COMPANY_ID,
                ACTOR_ID,
                LocalDate.of(2026, 8, 13),
                Instant.parse("2026-08-13T00:00:00Z")
        );

        seeder.seed(currentSeed, context);
        seeder.verifyExisting(currentSeed, context);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT from_status FROM task_transition_history WHERE transition_id = ?",
                String.class,
                TRANSITION_ID
        )).isEqualTo(TaskStatus.APPROVED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_transition_history WHERE transition_id = ?",
                Integer.class,
                TRANSITION_ID
        )).isEqualTo(1);
    }
}
