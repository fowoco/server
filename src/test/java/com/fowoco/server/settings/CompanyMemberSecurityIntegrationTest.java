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
                "spring.datasource.url=jdbc:h2:mem:fowoco-company-member-test;"
                        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CompanyMemberSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_A = UUID.fromString("71100000-0000-0000-0000-000000000001");
    private static final UUID HR_A = UUID.fromString("71100000-0000-0000-0000-000000000002");
    private static final UUID VIEWER_A = UUID.fromString("71100000-0000-0000-0000-000000000003");
    private static final UUID SUSPENDED_HR_A = UUID.fromString("71100000-0000-0000-0000-000000000004");
    private static final UUID DISABLED_ADMIN_A = UUID.fromString("71100000-0000-0000-0000-000000000005");
    private static final UUID ADMIN_B = UUID.fromString("72100000-0000-0000-0000-000000000001");
    private static final String PASSWORD = "Company-member-password-1!";

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

        insertCompany(COMPANY_A, "구성원 사업장 A");
        insertCompany(COMPANY_B, "구성원 사업장 B");
        insertSettings(COMPANY_A, "ADMIN_OR_HR");
        insertSettings(COMPANY_B, "ADMIN_ONLY");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(ADMIN_A, COMPANY_A, "member.admin.a@example.com", "가 관리자", "ADMIN", "ACTIVE", passwordHash);
        insertUser(HR_A, COMPANY_A, "member.hr.a@example.com", "나 인사", "HR", "ACTIVE", passwordHash);
        insertUser(VIEWER_A, COMPANY_A, "member.viewer.a@example.com", "다 조회", "VIEWER", "ACTIVE", passwordHash);
        insertUser(SUSPENDED_HR_A, COMPANY_A, "member.suspended.a@example.com", "라 휴면", "HR", "SUSPENDED", passwordHash);
        insertUser(DISABLED_ADMIN_A, COMPANY_A, "member.disabled.a@example.com", "마 비활성", "ADMIN", "DISABLED", passwordHash);
        insertUser(ADMIN_B, COMPANY_B, "member.admin.b@example.com", "가 타사", "ADMIN", "ACTIVE", passwordHash);
    }

    @Test
    void adminReceivesActiveMembersInStableOrderWithDerivedApprovalPermission() throws Exception {
        HttpResponse<String> response = get(login("member.admin.a@example.com"), "");

        assertThat(response.statusCode()).isEqualTo(200);
        List<Map<String, Object>> items = JsonPath.read(response.body(), "$.items");
        assertThat(items).hasSize(3);
        assertThat(JsonPath.<List<String>>read(response.body(), "$.items[*].display_name"))
                .containsExactly("가 관리자", "나 인사", "다 조회");
        assertThat(JsonPath.<List<String>>read(response.body(), "$.items[0].roles"))
                .containsExactly("ADMIN");
        assertThat(JsonPath.<Boolean>read(response.body(), "$.items[0].active")).isTrue();
        assertThat(JsonPath.<Boolean>read(response.body(), "$.items[0].approval_permission")).isTrue();
        assertThat(JsonPath.<Boolean>read(response.body(), "$.items[1].approval_permission")).isTrue();
        assertThat(JsonPath.<Boolean>read(response.body(), "$.items[2].approval_permission")).isFalse();
        assertThat(response.body()).doesNotContain(
                "email",
                "password",
                "normalized_email",
                "status",
                "version",
                COMPANY_B.toString()
        );
    }

    @Test
    void adminAndHrCanFilterByRoleActiveStateAndDerivedApprovalPermission() throws Exception {
        String adminToken = login("member.admin.a@example.com");
        HttpResponse<String> inactiveHr = get(
                adminToken,
                "?role=HR&approval_capable=false&active_only=false"
        );
        assertThat(inactiveHr.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(inactiveHr.body(), "$.items[*].display_name"))
                .containsExactly("라 휴면");
        assertThat(JsonPath.<Boolean>read(inactiveHr.body(), "$.items[0].active")).isFalse();
        assertThat(JsonPath.<Boolean>read(
                inactiveHr.body(),
                "$.items[0].approval_permission"
        )).isFalse();

        jdbcTemplate.update(
                "UPDATE company_settings SET approval_policy = 'ADMIN_ONLY' WHERE company_id = ?",
                COMPANY_A
        );
        HttpResponse<String> approvalCapable = get(
                login("member.hr.a@example.com"),
                "?approval_capable=true"
        );
        assertThat(JsonPath.<List<String>>read(approvalCapable.body(), "$.items[*].display_name"))
                .containsExactly("가 관리자");
    }

    @Test
    void viewerReceivesOnlyMinimalActiveProjectionAndCannotUseRestrictedFilters() throws Exception {
        String token = login("member.viewer.a@example.com");

        HttpResponse<String> response = get(token, "");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(response.body(), "$.items[*].display_name"))
                .containsExactly("가 관리자", "나 인사", "다 조회");
        assertThat(response.body()).doesNotContain(
                "roles",
                "active",
                "approval_permission",
                "email",
                "status"
        );
        assertAccessDenied(get(token, "?role=HR"));
        assertAccessDenied(get(token, "?approval_capable=true"));
        assertAccessDenied(get(token, "?active_only=false"));
        assertThat(get(token, "?active_only=true").statusCode()).isEqualTo(200);
    }

    @Test
    void queryNeverReturnsAnotherCompanyAndNoMatchReturnsEmptyItems() throws Exception {
        String token = login("member.admin.a@example.com");

        HttpResponse<String> response = get(token, "?role=VIEWER&approval_capable=true");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<Object>>read(response.body(), "$.items")).isEmpty();
        assertThat(response.body()).doesNotContain(COMPANY_B.toString(), "가 타사");
    }

    @Test
    void invalidQueryAndMissingAuthenticationUseTheCommonErrorContract() throws Exception {
        String token = login("member.admin.a@example.com");
        assertInvalidRequest(get(token, "?role=OWNER"));
        assertInvalidRequest(get(token, "?approval_capable=maybe"));
        assertInvalidRequest(get(token, "?active_only=maybe"));

        HttpResponse<String> unauthenticated = httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/company-members")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(unauthenticated.body(), "$.code"))
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private void assertAccessDenied(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("ACCESS_DENIED");
    }

    private void assertInvalidRequest(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isIn("INVALID_REQUEST", "VALIDATION_FAILED");
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> get(String token, String query) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/company-members" + query))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
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

    private void insertSettings(UUID companyId, String approvalPolicy) {
        jdbcTemplate.update(
                "INSERT INTO company_settings (company_id, approval_policy) VALUES (?, ?)",
                companyId,
                approvalPolicy
        );
    }

    private void insertUser(
            UUID userId,
            UUID companyId,
            String email,
            String displayName,
            String role,
            String status,
            String passwordHash
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email,
                    password_hash, role, status, display_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                companyId,
                email,
                email,
                passwordHash,
                role,
                status,
                displayName
        );
    }
}
