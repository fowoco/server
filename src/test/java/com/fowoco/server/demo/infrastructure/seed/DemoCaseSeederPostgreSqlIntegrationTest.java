package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class DemoCaseSeederPostgreSqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00.123456Z");

    @Test
    void storesAndReloadsCaseTimestampsAndRemainsIdempotentOnPostgreSql16() throws SQLException {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");
        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                username,
                password
        )) {
            migrate(url, username, password);
            verifyPostgreSqlBinding(url, username, password);
        }
    }

    private void verifyPostgreSqlBinding(String url, String username, String password)
            throws SQLException {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(
                        new SingleConnectionDataSource(connection, true)
                );
                insertCaseOwners(jdbcTemplate, companyId, actorId, workerId);
                DemoCaseSeeder seeder = new DemoCaseSeeder(jdbcTemplate, new ObjectMapper());
                DemoOperationalSeedContext context = new DemoOperationalSeedContext(
                        companyId,
                        actorId,
                        LocalDate.of(2026, 8, 7),
                        NOW
                );
                List<TaskSeed> tasks = List.of(taskSeed(taskId, caseId, workerId));

                seeder.seed(tasks, context);
                seeder.seed(tasks, context);

                StoredTimes stored = jdbcTemplate.queryForObject(
                        "SELECT created_at, updated_at FROM workflow_case WHERE case_id = ?",
                        (resultSet, rowNumber) -> new StoredTimes(
                                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
                        ),
                        caseId
                );
                assertThat(stored).isNotNull();
                assertThat(stored.createdAt())
                        .isEqualTo(NOW.minusSeconds(2L * 24 * 60 * 60));
                assertThat(stored.updatedAt()).isEqualTo(NOW);
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM workflow_case WHERE case_id = ?",
                        Integer.class,
                        caseId
                )).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    private void insertCaseOwners(
            JdbcTemplate jdbcTemplate,
            UUID companyId,
            UUID actorId,
            UUID workerId
    ) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status) VALUES (?, ?, 'ACTIVE')",
                companyId,
                "PostgreSQL Demo Seed Test"
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash, role, status
                ) VALUES (?, ?, ?, ?, 'test-password-hash', 'ADMIN', 'ACTIVE')
                """,
                actorId,
                companyId,
                "demo-seed-%s@example.com".formatted(actorId),
                "demo-seed-%s@example.com".formatted(actorId)
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality_code,
                    preferred_language, work_status
                ) VALUES (?, ?, 'PostgreSQL Seed Worker', 'VN', 'vi', 'ACTIVE')
                """,
                workerId,
                companyId
        );
    }

    private TaskSeed taskSeed(UUID taskId, UUID caseId, UUID workerId) {
        return new TaskSeed(
                taskId,
                caseId,
                workerId,
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

    private void migrate(String url, String username, String password) {
        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                .load()
                .migrate();
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private record StoredTimes(Instant createdAt, Instant updatedAt) {
    }
}
