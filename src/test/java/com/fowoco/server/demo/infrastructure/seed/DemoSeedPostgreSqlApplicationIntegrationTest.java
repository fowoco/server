package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.ServerApplication;
import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class DemoSeedPostgreSqlApplicationIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTATIVE_WORKER_ID =
            UUID.fromString("92000000-0000-0000-0000-000000000006");
    private static final UUID SHOWCASE_CASE_ID =
            UUID.fromString("94100000-0000-0000-0000-000000000001");
    private static final Map<String, Integer> EXPECTED_COUNTS = Map.ofEntries(
            Map.entry("user_account", 20),
            Map.entry("worker", 28),
            Map.entry("workflow_case", 21),
            Map.entry("task", 21),
            Map.entry("worker_document", 83),
            Map.entry("stored_file", 3),
            Map.entry("task_checklist_item", 60),
            Map.entry("approval_request", 15),
            Map.entry("task_transition_history", 54),
            Map.entry("external_submission", 6),
            Map.entry("task_evidence", 10),
            Map.entry("document_request_draft", 4),
            Map.entry("audit_event", 94)
    );

    @TempDir
    Path fileStoragePath;

    @Test
    void bootsFullDemoSeedAndRestartsIdempotentlyOnPostgreSql17() throws Exception {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");

        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                username,
                password
        )) {
            SeedSnapshot firstBoot;
            try (ConfigurableApplicationContext context = startApplication(
                    url,
                    username,
                    password
            )) {
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                assertGoldenFlowStartState(jdbcTemplate);
                firstBoot = snapshot(jdbcTemplate);
            }

            try (ConfigurableApplicationContext context = startApplication(
                    url,
                    username,
                    password
            )) {
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                assertGoldenFlowStartState(jdbcTemplate);
                assertThat(snapshot(jdbcTemplate)).isEqualTo(firstBoot);
            }
        }
    }

    private ConfigurableApplicationContext startApplication(
            String url,
            String username,
            String password
    ) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", url);
        properties.put("spring.datasource.username", username);
        properties.put("spring.datasource.password", password);
        properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        properties.put("spring.datasource.hikari.maximum-pool-size", "2");
        properties.put("spring.datasource.hikari.minimum-idle", "0");
        properties.put("spring.flyway.url", url);
        properties.put("spring.flyway.user", username);
        properties.put("spring.flyway.password", password);
        properties.put(
                "spring.flyway.locations",
                "classpath:db/migration,classpath:db/migration-postgresql"
        );
        properties.put("spring.jpa.show-sql", "false");
        properties.put("app.database.tenant-context-mode", "postgresql");
        properties.put("app.reliability.outbox.enabled", "false");
        properties.put("app.ai-runtime.enabled", "false");
        properties.put("app.demo-seed.enabled", "true");
        properties.put("app.demo-seed.admin-password", "Demo-password-1!");
        properties.put("app.file-storage.local-path", fileStoragePath.toString());
        properties.put(
                "app.auth.jwt.secret-base64",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        );
        properties.put("app.auth.refresh-token.cookie.secure", "false");
        properties.put("server.port", "0");

        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");
        environment.getPropertySources().addFirst(
                new MapPropertySource("postgresql-demo-seed-test", properties)
        );

        SpringApplication application = new SpringApplication(ServerApplication.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        return application.run();
    }

    private void assertGoldenFlowStartState(JdbcTemplate jdbcTemplate) {
        assertThat(countByCompany(jdbcTemplate, "worker", COMPANY_ID)).isEqualTo(28);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_case WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM worker_document
                WHERE worker_id = ? AND company_id = ?
                  AND document_type = 'PASSPORT_COPY'
                  AND submission_status = 'VERIFIED'
                  AND expiry_date > CURRENT_DATE
                  AND task_id IS NULL AND file_id IS NULL
                """,
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM worker_document
                WHERE worker_id = ? AND company_id = ?
                  AND document_type = 'ARC'
                  AND submission_status = 'MISSING'
                  AND expiry_date IS NULL
                  AND task_id IS NULL AND file_id IS NULL
                """,
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM worker_link link
                JOIN task linked_task
                  ON linked_task.task_id = link.task_id
                 AND linked_task.company_id = link.company_id
                WHERE linked_task.worker_id = ? AND link.company_id = ?
                """,
                Integer.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isZero();

        for (String table : new String[] {
                "ai_run",
                "ai_attempt",
                "ai_question",
                "ai_candidate",
                "ai_candidate_decision_batch",
                "ai_candidate_decision"
        }) {
            assertThat(countByCompany(jdbcTemplate, table, COMPANY_ID))
                    .as("%s must not be pre-seeded for the Golden Flow", table)
                    .isZero();
        }
    }

    private SeedSnapshot snapshot(JdbcTemplate jdbcTemplate) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        EXPECTED_COUNTS.forEach((table, expected) -> {
            int actual = countByCompany(jdbcTemplate, table, COMPANY_ID);
            assertThat(actual).as("%s count", table).isEqualTo(expected);
            counts.put(table, actual);
        });
        Timestamp showcaseCreatedAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM workflow_case WHERE case_id = ? AND company_id = ?",
                Timestamp.class,
                SHOWCASE_CASE_ID,
                COMPANY_ID
        );
        assertThat(showcaseCreatedAt).isNotNull();
        return new SeedSnapshot(Map.copyOf(counts), showcaseCreatedAt);
    }

    private int countByCompany(JdbcTemplate jdbcTemplate, String table, UUID companyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE company_id = ?",
                Integer.class,
                companyId
        );
        return count == null ? 0 : count;
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private record SeedSnapshot(Map<String, Integer> counts, Timestamp showcaseCreatedAt) {
    }
}
