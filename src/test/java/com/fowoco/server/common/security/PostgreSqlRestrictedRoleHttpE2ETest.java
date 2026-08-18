package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fowoco.server.common.error.GlobalExceptionHandler;
import com.fowoco.server.common.web.RequestIdFilter;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSqlRestrictedRoleHttpE2ETest {

    private static final String COOKIE_NAME = "fowoco_refresh_token";
    private static final String PROBE_PATH = "/api/v1/public/worker-links/rls-test/probes";
    private static final List<String> RLS_TABLES = List.of(
            "company",
            "user_account",
            "refresh_token",
            "worker",
            "task",
            "worker_link",
            "worker_response",
            "document_request_draft",
            "document_request_draft_type",
            "audit_event"
    );

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(
            GlobalExceptionHandler.class
    );
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    private PostgreSqlRestrictedRoleHttpDataFixture dataFixture;
    private PostgreSqlRestrictedRoleHttpEnvironment environment;
    private MeterRegistry meterRegistry;

    @BeforeAll
    void setUpRestrictedRoleHttpApplication() throws Exception {
        dataFixture = new PostgreSqlRestrictedRoleHttpDataFixture();
        environment = PostgreSqlRestrictedRoleHttpEnvironment.startFromEnvironment(
                dataFixture,
                ProbeConfiguration.class
        );
        meterRegistry = environment.bean(MeterRegistry.class);
        logAppender.start();
        handlerLogger.addAppender(logAppender);
    }

    @AfterAll
    void tearDownRestrictedRoleHttpApplication() throws Exception {
        handlerLogger.detachAppender(logAppender);
        logAppender.stop();
        if (environment != null) {
            environment.close();
            environment = null;
        }
    }

    @Test
    void restrictedRuntimeRoleHasOnlyTheExplicitHttpTestPrivileges() {
        RoleAttributes attributes = environment.migrationJdbc().queryForObject(
                """
                SELECT rolsuper, rolcreatedb, rolcreaterole, rolinherit,
                       rolreplication, rolbypassrls
                FROM pg_catalog.pg_roles
                WHERE rolname = ?
                """,
                (resultSet, rowNumber) -> new RoleAttributes(
                        resultSet.getBoolean("rolsuper"),
                        resultSet.getBoolean("rolcreatedb"),
                        resultSet.getBoolean("rolcreaterole"),
                        resultSet.getBoolean("rolinherit"),
                        resultSet.getBoolean("rolreplication"),
                        resultSet.getBoolean("rolbypassrls")
                ),
                environment.runtimeRole()
        );

        assertThat(attributes).isEqualTo(
                new RoleAttributes(false, false, false, false, false, false)
        );
        assertThat(environment.runtimeJdbc().queryForObject(
                "SELECT CURRENT_USER",
                String.class
        )).isEqualTo(environment.runtimeRole());
        assertThat(environment.migrationJdbc().queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_catalog.pg_auth_members membership
                JOIN pg_catalog.pg_roles member_role ON member_role.oid = membership.member
                WHERE member_role.rolname = ?
                """,
                Integer.class,
                environment.runtimeRole()
        )).isZero();
        assertThat(environment.migrationJdbc().queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_catalog.pg_tables
                WHERE schemaname = 'public' AND tableowner = ?
                """,
                Integer.class,
                environment.runtimeRole()
        )).isZero();
        assertThat(environment.runtimeJdbc().queryForObject(
                "SELECT pg_catalog.has_schema_privilege(CURRENT_USER, 'public', 'CREATE')",
                Boolean.class
        )).isFalse();
        assertThat(environment.runtimeJdbc().queryForObject(
                """
                SELECT pg_catalog.has_database_privilege(
                    CURRENT_USER, pg_catalog.current_database(), 'CREATE'
                )
                """,
                Boolean.class
        )).isFalse();

        assertTablePrivileges("company", true, false, false, false);
        assertTablePrivileges("user_account", true, false, true, false);
        assertTablePrivileges("refresh_token", true, true, true, false);
        assertTablePrivileges("worker", true, true, true, false);
        assertTablePrivileges("task", true, false, false, false);
        assertTablePrivileges("worker_link", true, false, false, false);
        assertTablePrivileges("worker_response", true, true, false, false);
        assertTablePrivileges("document_request_draft", true, false, false, false);
        assertTablePrivileges("document_request_draft_type", true, false, false, false);
        assertTablePrivileges("audit_event", true, true, false, false);

        for (String table : RLS_TABLES) {
            assertThat(environment.hasTablePrivilege(table, "TRUNCATE")).isFalse();
            assertThat(environment.hasTablePrivilege(table, "REFERENCES")).isFalse();
            assertThat(environment.migrationJdbc().queryForObject(
                    """
                    SELECT relation.relrowsecurity
                    FROM pg_catalog.pg_class relation
                    JOIN pg_catalog.pg_namespace namespace
                      ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public' AND relation.relname = ?
                    """,
                    Boolean.class,
                    table
            )).isTrue();
        }
        assertThat(environment.hasTablePrivilege("flyway_schema_history", "SELECT"))
                .isFalse();
        assertThat(environment.hasTablePrivilege("task", "SELECT")).isTrue();
        assertThat(environment.hasTablePrivilege("worker_document", "SELECT")).isFalse();
        assertThat(environment.hasFunctionPrivilege(
                "bootstrap_company_id_by_normalized_email(text)",
                "EXECUTE"
        )).isTrue();
        assertThat(environment.hasFunctionPrivilege(
                "bootstrap_company_id_by_refresh_token_hash(text)",
                "EXECUTE"
        )).isTrue();
        assertThat(environment.hasFunctionPrivilege(
                "bootstrap_company_id_by_worker_link_token_hash(text)",
                "EXECUTE"
        )).isTrue();
        assertThat(environment.hasFunctionPrivilege(
                "bootstrap_count_outstanding_event_publications()",
                "EXECUTE"
        )).isFalse();
    }

    @Test
    void loginAndRefreshBootstrapTheCorrectTenantWithRlsEnabled() throws Exception {
        HttpResponse<String> login = login(
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                PostgreSqlRestrictedRoleHttpDataFixture.PASSWORD
        );
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(login.body(), "$.company_id"))
                .isEqualTo(dataFixture.companyA().toString());

        String refreshToken = refreshToken(login);
        HttpResponse<String> refresh = postWithCookie(
                "/api/v1/auth/refresh",
                refreshToken
        );
        assertThat(refresh.statusCode()).isEqualTo(200);
        assertThat(refresh.body())
                .doesNotContain(refreshToken)
                .doesNotContain(PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL);

        String refreshedAccessToken = JsonPath.read(refresh.body(), "$.access_token");
        HttpResponse<String> ownWorker = get(
                "/api/v1/workers/" + dataFixture.workerA(),
                refreshedAccessToken
        );
        HttpResponse<String> hiddenWorker = get(
                "/api/v1/workers/" + dataFixture.workerB(),
                refreshedAccessToken
        );
        assertThat(ownWorker.statusCode()).isEqualTo(200);
        assertThat(hiddenWorker.statusCode()).isEqualTo(404);

        assertInvalidLogin(login("unknown.rls.http@example.test", "Wrong-password-1!"));
        assertInvalidLogin(login(
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                "Wrong-password-1!"
        ));
    }

    @Test
    void refreshRejectsUnknownExpiredAndRevokedTokensWithOneSafeContract()
            throws Exception {
        HttpResponse<String> unknown = postWithCookie(
                "/api/v1/auth/refresh",
                "u".repeat(43)
        );
        HttpResponse<String> expired = postWithCookie(
                "/api/v1/auth/refresh",
                PostgreSqlRestrictedRoleHttpDataFixture.EXPIRED_REFRESH_TOKEN
        );
        HttpResponse<String> revoked = postWithCookie(
                "/api/v1/auth/refresh",
                PostgreSqlRestrictedRoleHttpDataFixture.REVOKED_REFRESH_TOKEN
        );

        for (HttpResponse<String> response : List.of(unknown, expired, revoked)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(JsonPath.<String>read(response.body(), "$.code"))
                    .isEqualTo("INVALID_REFRESH_TOKEN");
            assertThat(response.body()).doesNotContain(
                    PostgreSqlRestrictedRoleHttpDataFixture.EXPIRED_REFRESH_TOKEN,
                    PostgreSqlRestrictedRoleHttpDataFixture.REVOKED_REFRESH_TOKEN,
                    PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL
            );
        }
    }

    @Test
    void workerLinkBootstrapAcceptsOnlyActiveRegisteredLinks() throws Exception {
        HttpResponse<String> active = get(
                "/api/v1/public/worker-links/"
                        + PostgreSqlRestrictedRoleHttpDataFixture.ACTIVE_WORKER_LINK_TOKEN,
                null
        );
        assertThat(active.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(active.body(), "$.guidance"))
                .isEqualTo("여권 사본을 제출해 주세요.");
        assertThat(JsonPath.<String>read(active.body(), "$.due_date"))
                .isEqualTo("2027-08-01");
        assertThat(JsonPath.<List<String>>read(active.body(), "$.requested_document_types"))
                .containsExactly("PASSPORT_COPY");
        assertThat(active.body()).doesNotContain(
                PostgreSqlRestrictedRoleHttpDataFixture.ACTIVE_WORKER_LINK_TOKEN,
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                dataFixture.companyB().toString()
        );

        HttpResponse<String> responseWrite = postJson(
                "/api/v1/public/worker-links/"
                        + PostgreSqlRestrictedRoleHttpDataFixture.ACTIVE_WORKER_LINK_TOKEN
                        + "/responses",
                """
                {
                  "response_type":"ACKNOWLEDGED",
                  "message":"RLS HTTP representative write",
                  "upload_ids":[],
                  "idempotency_key":"restricted-http-response"
                }
                """,
                null
        );
        assertThat(responseWrite.statusCode()).isEqualTo(201);
        assertThat(responseWrite.body()).doesNotContain(
                PostgreSqlRestrictedRoleHttpDataFixture.ACTIVE_WORKER_LINK_TOKEN,
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                dataFixture.companyB().toString()
        );

        assertThat(get(
                "/api/v1/public/worker-links/"
                        + PostgreSqlRestrictedRoleHttpDataFixture.EXPIRED_WORKER_LINK_TOKEN,
                null
        ).statusCode()).isEqualTo(410);
        assertThat(get(
                "/api/v1/public/worker-links/"
                        + PostgreSqlRestrictedRoleHttpDataFixture.REVOKED_WORKER_LINK_TOKEN,
                null
        ).statusCode()).isEqualTo(410);
        assertThat(get("/api/v1/public/worker-links/unregistered-rls-http-token", null).statusCode())
                .isEqualTo(410);
    }

    @Test
    void profileReadAndUpdateBindTheAuthenticatedTenantWithRlsEnabled() throws Exception {
        String accessToken = accessToken(login(
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                PostgreSqlRestrictedRoleHttpDataFixture.PASSWORD
        ));

        HttpResponse<String> currentProfile = get(
                "/api/v1/auth/me/profile",
                accessToken
        );

        assertThat(currentProfile.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(currentProfile.body(), "$.role")).isEqualTo("HR");
        assertThat(JsonPath.<String>read(currentProfile.body(), "$.account_status"))
                .isEqualTo("ACTIVE");

        HttpResponse<String> updatedProfile = patch(
                "/api/v1/auth/me/profile",
                """
                {
                  "display_name":"Restricted Profile A",
                  "phone":"010-1234-5678"
                }
                """,
                accessToken
        );

        assertThat(updatedProfile.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(updatedProfile.body(), "$.display_name"))
                .isEqualTo("Restricted Profile A");
        assertThat(JsonPath.<String>read(updatedProfile.body(), "$.phone"))
                .isEqualTo("010-1234-5678");
    }

    @Test
    void authenticatedWorkerRequestsRemainTenantIsolatedAcrossConnectionReuse()
            throws Exception {
        String companyAToken = accessToken(login(
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                PostgreSqlRestrictedRoleHttpDataFixture.PASSWORD
        ));
        String companyBToken = accessToken(login(
                PostgreSqlRestrictedRoleHttpDataFixture.USER_B_EMAIL,
                PostgreSqlRestrictedRoleHttpDataFixture.PASSWORD
        ));

        HttpResponse<String> companyAList = get("/api/v1/workers", companyAToken);
        assertThat(companyAList.statusCode()).isEqualTo(200);
        assertThat(companyAList.body())
                .contains(dataFixture.workerA().toString())
                .doesNotContain(dataFixture.workerB().toString());

        HttpResponse<String> crossTenantPatch = patch(
                "/api/v1/workers/" + dataFixture.workerB(),
                """
                {"display_name":"Cross tenant mutation","expected_version":0}
                """,
                companyAToken
        );
        assertThat(crossTenantPatch.statusCode()).isEqualTo(404);
        dataFixture.assertTenantBWorkerUnchanged();

        int companyAWorkersBefore = dataFixture.companyWorkerCount(dataFixture.companyA());
        HttpResponse<String> created = postJson(
                "/api/v1/workers",
                """
                {
                  "display_name":"Restricted HTTP Created Worker",
                  "nationality_code":"VN",
                  "preferred_language":"ko",
                  "stay_expiry_date":"2028-08-31",
                  "contract_start_date":"2027-09-01",
                  "contract_end_date":"2028-08-31"
                }
                """,
                companyAToken
        );
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(dataFixture.companyWorkerCount(dataFixture.companyA()))
                .isEqualTo(companyAWorkersBefore + 1);

        HttpResponse<String> companyBList = get("/api/v1/workers", companyBToken);
        assertThat(companyBList.statusCode()).isEqualTo(200);
        assertThat(companyBList.body())
                .contains(dataFixture.workerB().toString())
                .doesNotContain(dataFixture.workerA().toString())
                .doesNotContain("Restricted HTTP Created Worker");
    }

    @Test
    void unboundInsertProducesSafeObservedHttp500AndDoesNotCreateTheRow()
            throws Exception {
        double metricBefore = accessDeniedMetric();
        logAppender.list.clear();

        HttpResponse<String> response = postJson(
                PROBE_PATH + "/access-denied",
                "{}",
                null
        );

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("INTERNAL_SERVER_ERROR");
        String requestId = JsonPath.read(response.body(), "$.request_id");
        assertThat(requestId).isNotBlank();
        assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME))
                .contains(requestId);
        assertThat(JsonPath.<String>read(response.body(), "$.path"))
                .isEqualTo(PROBE_PATH + "/access-denied");
        assertThat(response.body()).doesNotContain(
                "42501",
                "INSERT INTO public.worker",
                "public.worker",
                "pl_worker_tenant_isolation",
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                PostgreSqlRestrictedRoleHttpDataFixture.ACTIVE_WORKER_LINK_TOKEN
        );
        assertThat(accessDeniedMetric()).isEqualTo(metricBefore + 1.0);
        dataFixture.assertUnboundWorkerWasNotInserted();

        List<ILoggingEvent> accessDeniedLogs = logAppender.list.stream()
                .filter(event -> event.getFormattedMessage()
                        .contains("classification=DATABASE_ACCESS_DENIED"))
                .toList();
        assertThat(accessDeniedLogs).hasSize(1);
        assertThat(accessDeniedLogs.get(0).getFormattedMessage())
                .contains("method=POST")
                .contains("route=" + PROBE_PATH + "/access-denied")
                .contains("sqlState=42501")
                .doesNotContain(
                        "INSERT INTO public.worker",
                        "pl_worker_tenant_isolation",
                        PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL,
                        PostgreSqlRestrictedRoleHttpDataFixture.ACTIVE_WORKER_LINK_TOKEN
                );
        assertThat(accessDeniedLogs.get(0).getThrowableProxy()).isNull();
    }

    @Test
    void ordinaryOptimisticLockKeepsTheExistingHttp409Contract() throws Exception {
        double metricBefore = accessDeniedMetric();
        HttpResponse<String> response = postJson(
                PROBE_PATH + "/optimistic-lock",
                "{}",
                null
        );

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(accessDeniedMetric()).isEqualTo(metricBefore);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return postJson(
                "/api/v1/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, password),
                null
        );
    }

    private void assertInvalidLogin(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.body()).doesNotContain(
                PostgreSqlRestrictedRoleHttpDataFixture.PASSWORD,
                PostgreSqlRestrictedRoleHttpDataFixture.USER_A_EMAIL
        );
    }

    private String accessToken(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private String refreshToken(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue(HttpHeaders.SET_COOKIE).orElseThrow();
        String prefix = COOKIE_NAME + "=";
        return List.of(setCookie.split(";", -1)).stream()
                .map(String::trim)
                .filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length()))
                .findFirst()
                .orElseThrow();
    }

    private HttpResponse<String> get(String path, String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithCookie(String path, String refreshToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.COOKIE, COOKIE_NAME + "=" + refreshToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String accessToken)
            throws Exception {
        return sendJson(path, body, accessToken, "POST");
    }

    private HttpResponse<String> patch(String path, String body, String accessToken)
            throws Exception {
        return sendJson(path, body, accessToken, "PATCH");
    }

    private HttpResponse<String> sendJson(
            String path,
            String body,
            String accessToken,
            String method
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + environment.port() + path);
    }

    private double accessDeniedMetric() {
        return meterRegistry.get("fowoco.database.access.denied").counter().count();
    }

    private void assertTablePrivileges(
            String table,
            boolean select,
            boolean insert,
            boolean update,
            boolean delete
    ) {
        assertThat(environment.hasTablePrivilege(table, "SELECT")).isEqualTo(select);
        assertThat(environment.hasTablePrivilege(table, "INSERT")).isEqualTo(insert);
        assertThat(environment.hasTablePrivilege(table, "UPDATE")).isEqualTo(update);
        assertThat(environment.hasTablePrivilege(table, "DELETE")).isEqualTo(delete);
    }

    private record RoleAttributes(
            boolean superuser,
            boolean createDatabase,
            boolean createRole,
            boolean inherit,
            boolean replication,
            boolean bypassRls
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        UnboundInsertProbeService unboundInsertProbeService(JdbcTemplate jdbcTemplate) {
            return new UnboundInsertProbeService(jdbcTemplate);
        }

        @Bean
        RestrictedRoleProbeController restrictedRoleProbeController(
                UnboundInsertProbeService probeService
        ) {
            return new RestrictedRoleProbeController(probeService);
        }
    }

    static class UnboundInsertProbeService {

        private final JdbcTemplate jdbcTemplate;

        UnboundInsertProbeService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public void insertWithoutTenantBinding() {
            jdbcTemplate.update(
                    """
                    INSERT INTO public.worker (
                        worker_id, company_id, display_name, work_status,
                        created_at, updated_at, version
                    ) VALUES (?, ?, 'Unbound HTTP probe', 'ACTIVE',
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """,
                    PostgreSqlRestrictedRoleHttpDataFixture.UNBOUND_INSERT_WORKER,
                    UUID.fromString("a9800000-0000-0000-0000-000000000001")
            );
        }
    }

    @RestController
    @RequestMapping(PROBE_PATH)
    static final class RestrictedRoleProbeController {

        private final UnboundInsertProbeService probeService;

        private RestrictedRoleProbeController(UnboundInsertProbeService probeService) {
            this.probeService = probeService;
        }

        @PostMapping("/access-denied")
        void accessDenied() {
            probeService.insertWithoutTenantBinding();
        }

        @PostMapping("/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
        }
    }
}
