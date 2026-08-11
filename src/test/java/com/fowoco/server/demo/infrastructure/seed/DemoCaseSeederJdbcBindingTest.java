package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class DemoCaseSeederJdbcBindingTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_ID =
            UUID.fromString("a1000000-0000-0000-0000-000000000003");
    private static final UUID CASE_ID =
            UUID.fromString("a1000000-0000-0000-0000-000000000004");
    private static final UUID TASK_ID =
            UUID.fromString("a1000000-0000-0000-0000-000000000005");
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00.123456Z");

    @Test
    void bindsCaseTimestampsExplicitlyAtTheJdbcBoundary() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        DemoCaseSeeder seeder = new DemoCaseSeeder(jdbcTemplate, new ObjectMapper());

        seeder.seed(
                List.of(taskSeed()),
                new DemoOperationalSeedContext(
                        COMPANY_ID,
                        ACTOR_ID,
                        LocalDate.of(2026, 8, 7),
                        NOW
                )
        );

        assertThat(jdbcTemplate.insertArguments[9])
                .isInstanceOf(Timestamp.class)
                .extracting(value -> ((Timestamp) value).toInstant())
                .isEqualTo(NOW.minusSeconds(2L * 24 * 60 * 60));
        assertThat(jdbcTemplate.insertArguments[10])
                .isInstanceOf(Timestamp.class)
                .extracting(value -> ((Timestamp) value).toInstant())
                .isEqualTo(NOW);
    }

    private static TaskSeed taskSeed() {
        return new TaskSeed(
                TASK_ID,
                CASE_ID,
                WORKER_ID,
                TaskType.RECONTRACT,
                "WF-CON-001",
                "PostgreSQL timestamp binding",
                "Verify explicit JDBC timestamp binding",
                TaskSource.MANUAL,
                TaskStatus.DRAFT,
                30,
                2,
                Map.of()
        );
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {

        private Object[] insertArguments;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(0);
        }

        @Override
        public int update(String sql, Object... args) {
            insertArguments = args;
            return 1;
        }
    }
}
