package com.fowoco.server.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskWorkflowIntegrationTest {

    private static final UUID COMPANY_A =
            UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID HR_A =
            UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_A =
            UUID.fromString("c2000000-0000-0000-0000-000000000001");
    private static final UUID HR_B =
            UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_A =
            UUID.fromString("c3000000-0000-0000-0000-000000000001");
    private static final String PASSWORD = "Test-password-1!";
    private static final String HR_A_EMAIL = "task.hr.a@example.com";
    private static final String VIEWER_A_EMAIL = "task.viewer.a@example.com";
    private static final String HR_B_EMAIL = "task.hr.b@example.com";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetAndSeed() {
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "Task 테스트 사업장 A");
        insertCompany(COMPANY_B, "Task 테스트 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash, "HR");
        insertUser(VIEWER_A, COMPANY_A, VIEWER_A_EMAIL, passwordHash, "VIEWER");
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash, "HR");
        insertWorker();
    }

    @Test
    void supportsTheCatalogTaskChecklistAndCancelApiFlow() throws Exception {
        String token = login(HR_A_EMAIL);

        HttpResponse<String> catalog = get("/api/v1/workflow-catalogs", token);
        assertThat(catalog.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(catalog.body(), "$.source_repository"))
                .isEqualTo("fowoco/knowledge");
        assertThat(JsonPath.<List<?>>read(catalog.body(), "$.workflows")).hasSize(2);

        HttpResponse<String> created = post("/api/v1/tasks", validCreateBody(), token);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue(HttpHeaders.LOCATION)).isPresent();
        UUID taskId = UUID.fromString(JsonPath.read(created.body(), "$.task_id"));
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("DRAFT");
        assertThat(JsonPath.<String>read(created.body(), "$.workflow_catalog_version"))
                .isEqualTo("0.2.0");
        assertThat(jdbcTemplate.queryForList(
                "SELECT event_type FROM event_publication "
                        + "WHERE aggregate_id = ? ORDER BY occurred_at",
                String.class,
                taskId
        )).containsExactly("TaskCreated");
        List<String> checklistIds = JsonPath.read(
                created.body(),
                "$.checklist_items[*].checklist_item_id"
        );
        assertThat(checklistIds).hasSize(2);

        HttpResponse<String> page = get(
                "/api/v1/tasks?worker_id=" + WORKER_A + "&status=DRAFT",
                token
        );
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<?>>read(page.body(), "$.items")).hasSize(1);

        HttpResponse<String> detail = get("/api/v1/tasks/" + taskId, token);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(detail.body(), "$.title")).isEqualTo("재계약 준비");

        HttpResponse<String> updated = patch(
                "/api/v1/tasks/" + taskId,
                """
                {
                  "title":"재계약 조건 확인",
                  "description":"임금과 계약기간 확인",
                  "due_date":"2026-08-20",
                  "business_data":{"monthly_wage":2500000},
                  "expected_version":0
                }
                """,
                token
        );
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Number>read(updated.body(), "$.version").longValue()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication "
                        + "WHERE aggregate_id = ? AND event_type = 'TaskCancelled'",
                Integer.class,
                taskId
        )).isZero();

        HttpResponse<String> checklist = patch(
                "/api/v1/tasks/" + taskId + "/checklist-items/" + checklistIds.get(0),
                """
                {
                  "completed":true,
                  "expected_version":0,
                  "expected_task_version":1
                }
                """,
                token
        );
        assertThat(checklist.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(
                checklist.body(),
                "$.checklist_items[0].completed"
        )).isTrue();

        HttpResponse<String> cancelled = post(
                "/api/v1/tasks/" + taskId + "/cancel",
                """
                {"expected_version":1,"reason":"근로계약 갱신 계획 변경"}
                """,
                token
        );
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cancelled.body(), "$.status")).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ?",
                Integer.class,
                taskId
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForList(
                "SELECT event_type FROM event_publication "
                        + "WHERE aggregate_id = ?",
                String.class,
                taskId
        )).containsExactlyInAnyOrder("TaskCreated", "TaskCancelled");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload_json FROM event_publication "
                        + "WHERE aggregate_id = ? AND event_type = 'TaskCancelled'",
                String.class,
                taskId
        ))
                .contains("\"previous_status\":\"NEEDS_INFO\"")
                .contains("\"status\":\"CANCELLED\"")
                .doesNotContain("display_name", "passport", "phone", "token");
    }

    @Test
    void missingRequiredSlotCreatesNeedsInfoAndCannotRequestApproval() throws Exception {
        String token = login(HR_A_EMAIL);
        String body = validCreateBody().replace("\"due_date\":\"2026-08-20\",", "");

        HttpResponse<String> created = post("/api/v1/tasks", body, token);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("NEEDS_INFO");
        assertThat(JsonPath.<List<String>>read(created.body(), "$.missing_required_slots"))
                .containsExactly("due_at");
    }

    @Test
    void viewerCannotWriteAndAnotherCompanyCannotDiscoverTask() throws Exception {
        String hrToken = login(HR_A_EMAIL);
        UUID taskId = UUID.fromString(JsonPath.read(
                post("/api/v1/tasks", validCreateBody(), hrToken).body(),
                "$.task_id"
        ));
        String viewerToken = login(VIEWER_A_EMAIL);
        String otherCompanyToken = login(HR_B_EMAIL);

        assertThat(post("/api/v1/tasks", validCreateBody(), viewerToken).statusCode())
                .isEqualTo(403);
        assertThat(get("/api/v1/tasks/" + taskId, otherCompanyToken).statusCode())
                .isEqualTo(404);
    }

    @Test
    void resignedAndTerminatedWorkersCannotReceiveNewTasks() throws Exception {
        String token = login(HR_A_EMAIL);

        for (String status : List.of("RESIGNED", "TERMINATED")) {
            jdbcTemplate.update(
                    "UPDATE worker SET work_status = ?, version = version + 1 WHERE worker_id = ?",
                    status,
                    WORKER_A
            );

            HttpResponse<String> response = post("/api/v1/tasks", validCreateBody(), token);

            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(JsonPath.<String>read(response.body(), "$.code"))
                    .isEqualTo("WORKER_NOT_ELIGIBLE");
        }
    }

    @Test
    void sensitiveBusinessDataAndStaleVersionAreRejected() throws Exception {
        String token = login(HR_A_EMAIL);
        String sensitive = validCreateBody().replace(
                "\"monthly_wage\":2500000",
                "\"phone\":\"010-1234-5678\""
        );
        HttpResponse<String> rejected = post("/api/v1/tasks", sensitive, token);
        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(JsonPath.<String>read(rejected.body(), "$.code"))
                .isEqualTo("SENSITIVE_TASK_DATA_REJECTED");

        UUID taskId = UUID.fromString(JsonPath.read(
                post("/api/v1/tasks", validCreateBody(), token).body(),
                "$.task_id"
        ));
        HttpResponse<String> stale = patch(
                "/api/v1/tasks/" + taskId,
                """
                {
                  "title":"오래된 화면 수정",
                  "description":null,
                  "due_date":"2026-08-20",
                  "business_data":{},
                  "expected_version":1
                }
                """,
                token
        );
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(JsonPath.<String>read(stale.body(), "$.code"))
                .isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void criticalEditInvalidatesAnApprovedSnapshot() throws Exception {
        String token = login(HR_A_EMAIL);
        HttpResponse<String> created = post("/api/v1/tasks", validCreateBody(), token);
        UUID taskId = UUID.fromString(JsonPath.read(created.body(), "$.task_id"));
        List<String> checklistIds = JsonPath.read(
                created.body(),
                "$.checklist_items[*].checklist_item_id"
        );
        for (String checklistId : checklistIds) {
            HttpResponse<String> checked = patch(
                    "/api/v1/tasks/" + taskId + "/checklist-items/" + checklistId,
                    """
                    {
                      "completed":true,
                      "expected_version":0,
                      "expected_task_version":0
                    }
                    """,
                    token
            );
            assertThat(checked.statusCode()).isEqualTo(200);
        }
        assertThat(post(
                "/api/v1/tasks/" + taskId + "/approval-requests",
                """
                {
                  "expected_version":0,
                  "ai_snapshot":null,
                  "hr_snapshot":{"worker_id":"%s"},
                  "changed_fields":[],
                  "source_versions":{"workflow_catalog_version":"0.2.0"}
                }
                """.formatted(WORKER_A),
                token
        ).statusCode()).isEqualTo(201);
        assertThat(post(
                "/api/v1/tasks/" + taskId + "/approve",
                """
                {"expected_version":1,"reason":"계약조건 확인"}
                """,
                token
        ).statusCode()).isEqualTo(200);

        HttpResponse<String> updated = patch(
                "/api/v1/tasks/" + taskId,
                """
                {
                  "title":"재계약 임금 변경",
                  "description":"승인 뒤 중요값 수정",
                  "due_date":"2026-08-21",
                  "business_data":{"monthly_wage":2700000},
                  "expected_version":2
                }
                """,
                token
        );

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(updated.body(), "$.status"))
                .isEqualTo("READY_FOR_REVIEW");
        assertThat(JsonPath.<Number>read(updated.body(), "$.version").longValue()).isEqualTo(4);
        assertThat(JsonPath.<Number>read(updated.body(), "$.content_revision").longValue())
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM approval_request WHERE task_id = ? ORDER BY created_at",
                String.class,
                taskId
        )).containsExactly("INVALIDATED", "PENDING");
    }

    @Test
    void concurrentTaskEditsAllowOnlyOneWinner() throws Exception {
        String token = login(HR_A_EMAIL);
        UUID taskId = UUID.fromString(JsonPath.read(
                post("/api/v1/tasks", validCreateBody(), token).body(),
                "$.task_id"
        ));
        String body = """
                {
                  "title":"동시 수정",
                  "description":"같은 version으로 동시에 수정",
                  "due_date":"2026-08-20",
                  "business_data":{"monthly_wage":2600000},
                  "expected_version":0
                }
                """;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpResponse<String>> first = executor.submit(
                    () -> patchAfterSignal(
                            "/api/v1/tasks/" + taskId,
                            body,
                            token,
                            ready,
                            start
                    )
            );
            Future<HttpResponse<String>> second = executor.submit(
                    () -> patchAfterSignal(
                            "/api/v1/tasks/" + taskId,
                            body,
                            token,
                            ready,
                            start
                    )
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS).statusCode(),
                    second.get(10, TimeUnit.SECONDS).statusCode()
            )).containsExactlyInAnyOrder(200, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM task WHERE task_id = ?",
                Long.class,
                taskId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event "
                        + "WHERE target_id = ? AND action = 'TASK_UPDATED'",
                Integer.class,
                taskId
        )).isEqualTo(1);
    }

    private String validCreateBody() {
        return """
                {
                  "worker_id":"%s",
                  "task_type":"RECONTRACT",
                  "workflow_id":"WF-CON-001",
                  "title":"재계약 준비",
                  "description":"기존 조건 확인",
                  "due_date":"2026-08-20",
                  "business_data":{"monthly_wage":2500000}
                }
                """.formatted(WORKER_A);
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD),
                null
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        authorize(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        authorize(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        authorize(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patchAfterSignal(
            String path,
            String body,
            String token,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent task update start signal timed out");
        }
        return patch(path, body, token);
    }

    private void authorize(HttpRequest.Builder builder, String token) {
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                companyId,
                name
        );
    }

    private void insertUser(
            UUID userId,
            UUID companyId,
            String email,
            String passwordHash,
            String role
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                userId,
                companyId,
                email,
                email,
                passwordHash,
                role
        );
    }

    private void insertWorker() {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality_code, preferred_language,
                    work_status, stay_expiry_date, contract_start_date, contract_end_date,
                    created_at, updated_at, version
                ) VALUES (?, ?, '근로자 A', 'VNM', 'vi', 'ACTIVE', ?, ?, ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                WORKER_A,
                COMPANY_A,
                LocalDate.of(2027, 8, 31),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31)
        );
    }
}
