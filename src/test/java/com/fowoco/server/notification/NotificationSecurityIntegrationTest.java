package com.fowoco.server.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("52000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("62000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("53000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("63000000-0000-0000-0000-000000000002");
    private static final UUID HR_A2 = UUID.fromString("53000000-0000-0000-0000-000000000002");
    private static final String HR_A_EMAIL = "hr.notification.a@example.com";
    private static final String HR_A2_EMAIL = "hr.notification.a2@example.com";
    private static final String HR_B_EMAIL = "hr.notification.b@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedCompaniesAndUsers() {
        jdbcTemplate.update("DELETE FROM notification");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "알림 사업장 A");
        insertCompany(COMPANY_B, "알림 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash);
        insertUser(HR_A2, COMPANY_A, HR_A2_EMAIL, passwordHash);
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash);
    }

    @BeforeEach
    void resetNotificationState() {
        jdbcTemplate.update("DELETE FROM notification");
    }

    @Test
    void listReturnsItemsAndUnreadCount() throws Exception {
        insertNotification(COMPANY_A, "TASK", false, Instant.now().minus(1, ChronoUnit.HOURS));
        insertNotification(COMPANY_A, "TASK", true, Instant.now().minus(2, ChronoUnit.HOURS));
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/notifications", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.items")).hasSize(2);
        assertThat(JsonPath.<Number>read(response.body(), "$.unread_count").longValue()).isEqualTo(1);
    }

    @Test
    void unreadOnlyFiltersReadNotifications() throws Exception {
        insertNotification(COMPANY_A, "TASK", false, Instant.now());
        insertNotification(COMPANY_A, "TASK", true, Instant.now());
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/notifications?unreadOnly=true", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.items")).hasSize(1);
    }

    @Test
    void otherCompanyNotificationsAreNotVisible() throws Exception {
        insertNotification(COMPANY_B, "TASK", false, Instant.now());
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/notifications", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.items")).isEmpty();
    }

    @Test
    void readMarksNotificationAsReadAndIsIdempotent() throws Exception {
        UUID notificationId = insertNotification(COMPANY_A, "TASK", false, Instant.now());
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> firstRead = authorizedPost(
                "/api/v1/notifications/" + notificationId + "/read", accessToken
        );
        HttpResponse<String> secondRead = authorizedPost(
                "/api/v1/notifications/" + notificationId + "/read", accessToken
        );

        assertThat(firstRead.statusCode()).isEqualTo(204);
        assertThat(secondRead.statusCode()).isEqualTo(204);

        HttpResponse<String> listResponse = authorizedGet("/api/v1/notifications", accessToken);
        assertThat(JsonPath.<Boolean>read(listResponse.body(), "$.items[0].read")).isTrue();
    }

    @Test
    void readOnOtherCompanyNotificationReturnsNotFound() throws Exception {
        UUID notificationId = insertNotification(COMPANY_B, "TASK", false, Instant.now());
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedPost(
                "/api/v1/notifications/" + notificationId + "/read", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void notificationsAreIsolatedBetweenUsersInSameCompany() throws Exception {
        insertNotification(COMPANY_A, HR_A, "TASK", false, Instant.now());
        String hrA2Token = accessToken(login(HR_A2_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/notifications", hrA2Token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.items")).isEmpty();
        assertThat(JsonPath.<Number>read(response.body(), "$.unread_count").longValue()).isZero();
    }

    @Test
    void hasNextIsFalseWhenExactlySizeItemsRemain() throws Exception {
        for (int i = 0; i < 3; i++) {
            insertNotification(COMPANY_A, HR_A, "TASK", false, Instant.now().minusSeconds(i));
        }
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/notifications?size=3", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.items")).hasSize(3);
        assertThat(JsonPath.<Boolean>read(response.body(), "$.has_next")).isFalse();
    }

    @Test
    void hasNextIsTrueWhenMoreItemsRemain() throws Exception {
        for (int i = 0; i < 4; i++) {
            insertNotification(COMPANY_A, HR_A, "TASK", false, Instant.now().minusSeconds(i));
        }
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/notifications?size=3", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$.items")).hasSize(3);
        assertThat(JsonPath.<Boolean>read(response.body(), "$.has_next")).isTrue();
    }

    @Test
    void cursorAndUnreadOnlyFiltersCanBeCombined() throws Exception {
        Instant cursor = Instant.parse("2026-08-11T02:00:00Z");
        insertNotification(COMPANY_A, HR_A, "TASK", false, cursor.minusSeconds(1));
        insertNotification(COMPANY_A, HR_A, "TASK", true, cursor.minusSeconds(2));
        insertNotification(COMPANY_A, HR_A, "TASK", false, cursor.plusSeconds(1));
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> page = authorizedGet(
                "/api/v1/notifications?cursor=" + cursor,
                accessToken
        );
        HttpResponse<String> unreadPage = authorizedGet(
                "/api/v1/notifications?unreadOnly=true&cursor=" + cursor,
                accessToken
        );

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(page.body(), "$.items")).hasSize(2);
        assertThat(unreadPage.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(unreadPage.body(), "$.items")).hasSize(1);
    }

    @Test
    void listPreferencesReturnsDefaultsWhenNothingStored() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedGet("/api/v1/notifications/preferences", accessToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<java.util.List<?>>read(response.body(), "$")).hasSize(7);
        assertThat(preferenceEnabled(response.body(), "security-permission")).isTrue();
        assertThat(preferenceRequired(response.body(), "security-permission")).isTrue();
        assertThat(preferenceEnabled(response.body(), "assigned")).isFalse();
    }

    @Test
    void updatePreferencePersistsAcrossRequests() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> updateResponse = authorizedPatch(
                "/api/v1/notifications/preferences/due-soon", "{\"enabled\":false}", accessToken
        );
        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(preferenceEnabled(updateResponse.body(), "due-soon")).isFalse();

        HttpResponse<String> listResponse = authorizedGet("/api/v1/notifications/preferences", accessToken);
        assertThat(preferenceEnabled(listResponse.body(), "due-soon")).isFalse();
    }

    @Test
    void updatingRequiredPreferenceToDisabledIsRejected() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedPatch(
                "/api/v1/notifications/preferences/security-permission", "{\"enabled\":false}", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(422);
    }

    @Test
    void updatingUnknownPreferenceKeyReturnsNotFound() throws Exception {
        String accessToken = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = authorizedPatch(
                "/api/v1/notifications/preferences/not-a-real-key", "{\"enabled\":false}", accessToken
        );

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void preferencesAreIsolatedPerUser() throws Exception {
        String hrAToken = accessToken(login(HR_A_EMAIL));
        String hrA2Token = accessToken(login(HR_A2_EMAIL));
        authorizedPatch("/api/v1/notifications/preferences/agent-ready", "{\"enabled\":false}", hrAToken);

        HttpResponse<String> otherUserList = authorizedGet("/api/v1/notifications/preferences", hrA2Token);

        assertThat(preferenceEnabled(otherUserList.body(), "agent-ready")).isTrue();
    }

    private boolean preferenceEnabled(String body, String key) {
        java.util.List<Boolean> matches = JsonPath.read(body, "$[?(@.key=='" + key + "')].enabled");
        return matches.get(0);
    }

    private boolean preferenceRequired(String body, String key) {
        java.util.List<Boolean> matches = JsonPath.read(body, "$[?(@.key=='" + key + "')].required");
        return matches.get(0);
    }

    private UUID insertNotification(UUID companyId, String targetType, boolean read, Instant occurredAt) {
        return insertNotification(companyId, HR_A, targetType, read, occurredAt);
    }

    private UUID insertNotification(
            UUID companyId, UUID userId, String targetType, boolean read, Instant occurredAt
    ) {
        UUID notificationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO notification (
                    notification_id, company_id, user_id, target_type, target_id, route,
                    title, is_read, occurred_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                notificationId, companyId, userId, targetType, UUID.randomUUID(), "/tasks/" + UUID.randomUUID(),
                "테스트 알림", read, java.sql.Timestamp.from(occurredAt), java.sql.Timestamp.from(Instant.now())
        );
        return notificationId;
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                companyId, name
        );
    }

    private void insertUser(UUID userId, UUID companyId, String email, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                userId, companyId, email, email, passwordHash
        );
    }

    private HttpResponse<String> login(String email) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
        return postJson("/api/v1/auth/login", body, null);
    }

    private String accessToken(HttpResponse<String> loginResponse) {
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        return JsonPath.read(loginResponse.body(), "$.access_token");
    }

    private HttpResponse<String> authorizedGet(String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authorizedPost(String path, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String accessToken) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> authorizedPatch(String path, String body, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
