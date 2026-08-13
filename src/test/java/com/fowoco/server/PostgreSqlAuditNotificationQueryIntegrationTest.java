package com.fowoco.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSqlAuditNotificationQueryIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final UUID AUDIT_EVENT_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000003");
    private static final UUID NOTIFICATION_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000004");
    private static final UUID TARGET_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000005");
    private static final String EMAIL = "postgres.query-regression@example.com";
    private static final String PASSWORD = "Test-password-1!";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T02:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private PostgreSqlRlsTestLock databaseLock;

    @DynamicPropertySource
    static void usePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", () -> required("POSTGRES_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> required("POSTGRES_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-postgresql"
        );
    }

    @BeforeAll
    void acquireDatabaseLock() throws Exception {
        databaseLock = PostgreSqlRlsTestLock.acquire(
                required("POSTGRES_TEST_URL"),
                required("POSTGRES_TEST_USERNAME"),
                required("POSTGRES_TEST_PASSWORD")
        );
    }

    @AfterAll
    void releaseDatabaseLock() throws Exception {
        if (databaseLock != null) {
            databaseLock.close();
            databaseLock = null;
        }
    }

    @BeforeEach
    void seedQueryData() {
        cleanQueryData();
        Timestamp now = Timestamp.from(OCCURRED_AT);
        jdbcTemplate.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, 'PostgreSQL query regression', 'ACTIVE', ?, ?, 0)
                """,
                COMPANY_ID,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO company_settings (company_id) VALUES (?)",
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'ADMIN', 'ACTIVE', ?, ?, 0)
                """,
                ADMIN_ID,
                COMPANY_ID,
                EMAIL,
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                now,
                now
        );
        jdbcTemplate.update(
                """
                INSERT INTO audit_event (
                    audit_event_id, company_id, actor_type, actor_id, user_role,
                    action, target_type, target_id, request_id,
                    event_version, change_summary, created_at
                ) VALUES (?, ?, 'HR_USER', ?, 'ADMIN', 'TASK_CREATED', 'TASK', ?, ?, '1', ?, ?)
                """,
                AUDIT_EVENT_ID,
                COMPANY_ID,
                ADMIN_ID,
                TARGET_ID,
                "postgres-query-regression",
                "PostgreSQL 감사 조회 회귀 검증",
                now
        );
        jdbcTemplate.update(
                """
                INSERT INTO notification (
                    notification_id, company_id, user_id, target_type, target_id,
                    route, title, is_read, occurred_at, created_at
                ) VALUES (?, ?, ?, 'TASK', ?, ?, ?, false, ?, ?)
                """,
                NOTIFICATION_ID,
                COMPANY_ID,
                ADMIN_ID,
                TARGET_ID,
                "/tasks/" + TARGET_ID,
                "PostgreSQL 알림 조회 회귀 검증",
                now,
                now
        );
    }

    @AfterEach
    void cleanUpQueryData() {
        cleanQueryData();
    }

    @Test
    void nullableFiltersDoNotBreakAuditAndNotificationHttpQueries() throws Exception {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_catalog.version()",
                String.class
        )).startsWith("PostgreSQL");
        String accessToken = accessToken(login());

        HttpResponse<String> auditResponse = authorizedGet(
                "/api/v1/audit-events?limit=100",
                accessToken
        );
        HttpResponse<String> notificationResponse = authorizedGet(
                "/api/v1/notifications?size=20",
                accessToken
        );

        assertThat(auditResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(
                auditResponse.body(),
                "$.items[*].audit_event_id"
        )).contains(AUDIT_EVENT_ID.toString());
        assertThat(notificationResponse.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(
                notificationResponse.body(),
                "$.items[*].id"
        )).contains(NOTIFICATION_ID.toString());
    }

    private void cleanQueryData() {
        jdbcTemplate.update("DELETE FROM notification WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM audit_event WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM refresh_token WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM user_account WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM company_settings WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM company WHERE company_id = ?", COMPANY_ID);
    }

    private HttpResponse<String> login() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"%s","password":"%s"}
                        """.formatted(EMAIL, PASSWORD)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String accessToken(HttpResponse<String> loginResponse) {
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        return JsonPath.read(loginResponse.body(), "$.access_token");
    }

    private HttpResponse<String> authorizedGet(String path, String accessToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }
}
