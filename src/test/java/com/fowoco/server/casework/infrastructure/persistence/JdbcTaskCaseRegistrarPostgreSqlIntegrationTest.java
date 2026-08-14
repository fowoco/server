package com.fowoco.server.casework.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import com.fowoco.server.task.application.port.TaskCaseRegistrar.CaseTask;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class JdbcTaskCaseRegistrarPostgreSqlIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-13T13:22:48.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-13T13:23:48.654321Z");

    @Test
    void registersCompositeCandidateCaseWithInstantTimestampsOnPostgreSql16() throws SQLException {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");
        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                username,
                password
        )) {
            migrate(url, username, password);
            verifyCandidateCaseRegistration(url, username, password);
        }
    }

    private void verifyCandidateCaseRegistration(String url, String username, String password)
            throws SQLException {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(
                        new SingleConnectionDataSource(connection, true)
                );
                insertCaseOwners(jdbcTemplate, companyId, actorId, workerId);
                JdbcTaskCaseRegistrar registrar = new JdbcTaskCaseRegistrar(
                        jdbcTemplate,
                        new ObjectMapper()
                );

                registrar.registerComposite(
                        List.of(
                                caseTask(companyId, actorId, workerId, caseId, 1),
                                caseTask(companyId, actorId, workerId, caseId, 2),
                                caseTask(companyId, actorId, workerId, caseId, 3)
                        ),
                        LocalDate.of(2026, 8, 13)
                );

                StoredCase stored = jdbcTemplate.queryForObject(
                        """
                        SELECT created_at, updated_at, workflow_snapshot_json
                        FROM workflow_case
                        WHERE case_id = ? AND company_id = ?
                        """,
                        (resultSet, rowNumber) -> new StoredCase(
                                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                                resultSet.getString("workflow_snapshot_json")
                        ),
                        caseId,
                        companyId
                );
                assertThat(stored).isNotNull();
                assertThat(stored.createdAt()).isEqualTo(CREATED_AT);
                assertThat(stored.updatedAt()).isEqualTo(UPDATED_AT);
                assertThat(stored.workflowSnapshotJson()).contains(
                        "\"order\":1",
                        "\"order\":2",
                        "\"order\":3"
                );
            } finally {
                connection.rollback();
            }
        }
    }

    private CaseTask caseTask(
            UUID companyId,
            UUID actorId,
            UUID workerId,
            UUID caseId,
            int candidateOrder
    ) {
        Task task = new Task(
                UUID.randomUUID(),
                companyId,
                workerId,
                caseId,
                TaskType.STAY_PERIOD_EXTENSION,
                "WF-STY-001",
                "0.2.0",
                "체류기간 연장 준비",
                null,
                "{\"candidate_order\":%d}".formatted(candidateOrder),
                "0".repeat(64),
                0,
                TaskSource.AI_CANDIDATE,
                TaskStatus.DRAFT,
                LocalDate.of(2026, 9, 30),
                actorId,
                actorId,
                CREATED_AT,
                UPDATED_AT,
                0
        );
        WorkflowDefinition workflow = new WorkflowDefinition(
                "WF-STY-001",
                "체류기간 연장",
                "EXPIRY_RENEWAL",
                "high",
                Set.of(TaskType.STAY_PERIOD_EXTENSION),
                Set.of("worker_id", "stay_expiry_date"),
                Set.of("worker_id", "stay_expiry_date"),
                Set.of("worker_id", "stay_expiry_date"),
                List.of(),
                List.of(),
                List.of()
        );
        return new CaseTask(task, workflow);
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
                "PostgreSQL Candidate Decision Test"
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash, role, status
                ) VALUES (?, ?, ?, ?, 'test-password-hash', 'HR', 'ACTIVE')
                """,
                actorId,
                companyId,
                "candidate-%s@example.com".formatted(actorId),
                "candidate-%s@example.com".formatted(actorId)
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality_code,
                    preferred_language, work_status
                ) VALUES (?, ?, 'PostgreSQL Candidate Worker', 'VN', 'vi', 'ACTIVE')
                """,
                workerId,
                companyId
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

    private record StoredCase(
            Instant createdAt,
            Instant updatedAt,
            String workflowSnapshotJson
    ) {
    }
}
