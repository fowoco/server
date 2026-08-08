package com.fowoco.server.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:fowoco-company-settings-get-test;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CompanySettingsSecurityIntegrationTest {

    private static final UUID COMPANY_A =
            UUID.fromString("61000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("62000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_A =
            UUID.fromString("61100000-0000-0000-0000-000000000001");
    private static final UUID HR_A =
            UUID.fromString("61100000-0000-0000-0000-000000000002");
    private static final UUID VIEWER_A =
            UUID.fromString("61100000-0000-0000-0000-000000000003");
    private static final UUID ADMIN_B =
            UUID.fromString("62100000-0000-0000-0000-000000000001");
    private static final String PASSWORD = "Settings-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetAndSeed() {
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "설정 조회 사업장 A");
        insertCompany(COMPANY_B, "설정 조회 사업장 B");
        insertSettings(
                COMPANY_A,
                "ADMIN_OR_HR",
                48,
                "{\"RECONTRACT\":[\"DOCUMENT\"],\"STAY_PERIOD_EXTENSION\":[\"OFFICIAL_RESULT\"]}",
                730,
                120,
                "ADMIN_AND_HR",
                4
        );
        insertSettings(COMPANY_B, "ADMIN_ONLY", 24, "{}", 365, 30, "ADMIN_ONLY", 2);

        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(ADMIN_A, COMPANY_A, "settings.admin.a@example.com", "ADMIN", passwordHash);
        insertUser(HR_A, COMPANY_A, "settings.hr.a@example.com", "HR", passwordHash);
        insertUser(VIEWER_A, COMPANY_A, "settings.viewer.a@example.com", "VIEWER", passwordHash);
        insertUser(ADMIN_B, COMPANY_B, "settings.admin.b@example.com", "ADMIN", passwordHash);
    }

    @Test
    void adminHrAndViewerReceiveTheSamePublicSettingsDtoWithoutSensitiveFields() throws Exception {
        HttpResponse<String> admin = get(login("settings.admin.a@example.com"));
        HttpResponse<String> hr = get(login("settings.hr.a@example.com"));
        HttpResponse<String> viewer = get(login("settings.viewer.a@example.com"));

        assertThat(admin.statusCode()).isEqualTo(200);
        assertThat(hr.statusCode()).isEqualTo(200);
        assertThat(viewer.statusCode()).isEqualTo(200);
        Map<String, Object> adminBody = JsonPath.read(admin.body(), "$");
        assertThat(JsonPath.<Map<String, Object>>read(hr.body(), "$")).isEqualTo(adminBody);
        assertThat(JsonPath.<Map<String, Object>>read(viewer.body(), "$")).isEqualTo(adminBody);
        assertThat(JsonPath.<String>read(admin.body(), "$.approval_policy"))
                .isEqualTo("ADMIN_OR_HR");
        assertThat(JsonPath.<Number>read(admin.body(), "$.link_expiry_hours").longValue())
                .isEqualTo(48L);
        assertThat(JsonPath.<String>read(admin.body(), "$.evidence_rules.RECONTRACT[0]"))
                .isEqualTo("DOCUMENT");
        assertThat(JsonPath.<Number>read(admin.body(), "$.file_retention_days").intValue())
                .isEqualTo(730);
        assertThat(JsonPath.<Number>read(admin.body(), "$.ai_log_retention_days").intValue())
                .isEqualTo(120);
        assertThat(JsonPath.<String>read(admin.body(), "$.audit_visibility"))
                .isEqualTo("ADMIN_AND_HR");
        assertThat(JsonPath.<Number>read(admin.body(), "$.version").longValue()).isEqualTo(4L);
        assertThat(admin.body()).doesNotContain(
                "company_id",
                "email",
                "password",
                "token",
                "secret",
                "created_at",
                "updated_at"
        );
    }

    @Test
    void settingsAreResolvedOnlyFromTheAuthenticatedCompany() throws Exception {
        HttpResponse<String> companyA = get(login("settings.admin.a@example.com"));
        HttpResponse<String> companyB = get(login("settings.admin.b@example.com"));

        assertThat(JsonPath.<Number>read(companyA.body(), "$.link_expiry_hours").longValue())
                .isEqualTo(48L);
        assertThat(JsonPath.<Number>read(companyB.body(), "$.link_expiry_hours").longValue())
                .isEqualTo(24L);
        assertThat(JsonPath.<String>read(companyB.body(), "$.approval_policy"))
                .isEqualTo("ADMIN_ONLY");
        assertThat(companyA.body()).doesNotContain(COMPANY_B.toString());
        assertThat(companyB.body()).doesNotContain(COMPANY_A.toString());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/settings")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void missingPersistedSettingsRowIsAnInternalInvariantViolation() throws Exception {
        String token = login("settings.admin.a@example.com");
        jdbcTemplate.update("DELETE FROM company_settings WHERE company_id = ?", COMPANY_A);

        HttpResponse<String> response = get(token);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.body()).doesNotContain(COMPANY_A.toString(), "Persisted company settings");
    }

    @Test
    void adminPartiallyUpdatesSettingsAndRecordsCompactBeforeAfterAudit() throws Exception {
        String token = login("settings.admin.a@example.com");

        HttpResponse<String> response = patch(token, """
                {
                  "expected_version":4,
                  "approval_policy":"ADMIN_ONLY",
                  "link_expiry_hours":72,
                  "evidence_rules":{"RECONTRACT":["DOCUMENT","RECEIPT"]}
                }
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.approval_policy"))
                .isEqualTo("ADMIN_ONLY");
        assertThat(JsonPath.<Number>read(response.body(), "$.link_expiry_hours").longValue())
                .isEqualTo(72L);
        assertThat(JsonPath.<Number>read(response.body(), "$.file_retention_days").intValue())
                .isEqualTo(730);
        assertThat(JsonPath.<Number>read(response.body(), "$.version").longValue()).isEqualTo(5L);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                SELECT action, target_type, target_id, actor_id, user_role,
                       event_version, change_summary
                FROM audit_event
                WHERE company_id = ?
                """,
                COMPANY_A
        );
        assertThat(audit)
                .containsEntry("action", "COMPANY_SETTINGS_UPDATED")
                .containsEntry("target_type", "COMPANY_SETTINGS")
                .containsEntry("target_id", COMPANY_A)
                .containsEntry("actor_id", ADMIN_A)
                .containsEntry("user_role", "ADMIN")
                .containsEntry("event_version", "1");
        assertThat(audit.get("change_summary").toString())
                .isEqualTo("{\"approval_policy\":[\"ADMIN_OR_HR\",\"ADMIN_ONLY\"],"
                        + "\"link_expiry_hours\":[48,72],"
                        + "\"evidence_rules\":[\"REC=D;SPE=O\",\"REC=DR\"]}");
    }

    @Test
    void noOpKeepsVersionWithoutAuditAndStaleVersionReturnsConflict() throws Exception {
        String token = login("settings.admin.a@example.com");

        HttpResponse<String> noOp = patch(token, "{\"expected_version\":4}");
        HttpResponse<String> stale = patch(token, """
                {"expected_version":3,"link_expiry_hours":72}
                """);

        assertThat(noOp.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(noOp.body(), "$.version").longValue()).isEqualTo(4L);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(stale.body(), "$.code"))
                .isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE company_id = ?",
                Integer.class,
                COMPANY_A
        )).isZero();
    }

    @Test
    void patchRejectsExplicitNullUnknownFieldsMissingVersionAndOutOfRangeValues() throws Exception {
        String token = login("settings.admin.a@example.com");

        assertThat(patch(token, "{\"expected_version\":4,\"approval_policy\":null}").statusCode())
                .isEqualTo(400);
        assertThat(patch(token, "{\"expected_version\":4,\"company_id\":\"" + COMPANY_B
                + "\"}").statusCode()).isEqualTo(400);
        assertThat(patch(token, "{}").statusCode()).isEqualTo(400);
        assertThat(patch(token, "{\"expected_version\":4,\"link_expiry_hours\":169}")
                .statusCode()).isEqualTo(400);
    }

    @Test
    void hrAndViewerCannotPatchSettings() throws Exception {
        String body = "{\"expected_version\":4,\"link_expiry_hours\":72}";

        assertThat(patch(login("settings.hr.a@example.com"), body).statusCode()).isEqualTo(403);
        assertThat(patch(login("settings.viewer.a@example.com"), body).statusCode()).isEqualTo(403);
    }

    @Test
    void concurrentPatchAllowsOnlyOneWriterForTheExpectedVersion() throws Exception {
        String token = login("settings.admin.a@example.com");
        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(
                () -> patchUnchecked(token, "{\"expected_version\":4,\"link_expiry_hours\":72}")
        );
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(
                () -> patchUnchecked(token, "{\"expected_version\":4,\"link_expiry_hours\":96}")
        );

        assertThat(List.of(first.join().statusCode(), second.join().statusCode()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE company_id = ?",
                Integer.class,
                COMPANY_A
        )).isEqualTo(1);
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaTypeValues.APPLICATION_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> get(String token) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/settings"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> patch(String token, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/settings"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.CONTENT_TYPE, MediaTypeValues.APPLICATION_JSON)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> patchUnchecked(String token, String body) {
        try {
            return patch(token, body);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status) VALUES (?, ?, 'ACTIVE')",
                companyId,
                name
        );
    }

    private void insertSettings(
            UUID companyId,
            String approvalPolicy,
            long linkExpiryHours,
            String evidenceRulesJson,
            int fileRetentionDays,
            int aiLogRetentionDays,
            String auditVisibility,
            long version
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO company_settings (
                    company_id, approval_policy, link_expiry_hours, evidence_rules_json,
                    file_retention_days, ai_log_retention_days, audit_visibility, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                companyId,
                approvalPolicy,
                linkExpiryHours,
                evidenceRulesJson,
                fileRetentionDays,
                aiLogRetentionDays,
                auditVisibility,
                version
        );
    }

    private void insertUser(
            UUID userId,
            UUID companyId,
            String email,
            String role,
            String passwordHash
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status, display_name
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """,
                userId,
                companyId,
                email,
                email,
                passwordHash,
                role,
                role + " 설정 조회자"
        );
    }

    private static final class MediaTypeValues {
        private static final String APPLICATION_JSON = "application/json";

        private MediaTypeValues() {
        }
    }
}
