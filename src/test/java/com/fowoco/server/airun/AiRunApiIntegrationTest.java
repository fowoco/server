package com.fowoco.server.airun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiQuestion;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiRunApiIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("82000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final String HR_A_EMAIL = "airun.hr.a@example.com";
    private static final String HR_B_EMAIL = "airun.hr.b@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AiRuntimeClient runtimeClient;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private AtomicInteger runtimeCalls;

    @BeforeEach
    void resetAndSeed() {
        reset(runtimeClient);
        runtimeCalls = new AtomicInteger();
        when(runtimeClient.analyze(any(), any())).thenAnswer(invocation -> {
            AiAnalysisRequest request = invocation.getArgument(0);
            return scriptedResponse(request, runtimeCalls.incrementAndGet());
        });

        jdbcTemplate.update("DELETE FROM ai_candidate_decision_task");
        jdbcTemplate.update("DELETE FROM ai_candidate_decision");
        jdbcTemplate.update("DELETE FROM ai_candidate_decision_batch");
        jdbcTemplate.update("DELETE FROM ai_candidate");
        jdbcTemplate.update("DELETE FROM ai_question");
        jdbcTemplate.update("DELETE FROM ai_attempt");
        jdbcTemplate.update("DELETE FROM ai_run");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM workflow_case");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM user_account");
        jdbcTemplate.update("DELETE FROM company");

        insertCompany(COMPANY_A, "AI Run 사업장 A");
        insertCompany(COMPANY_B, "AI Run 사업장 B");
        String passwordHash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, passwordHash);
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, passwordHash);
        insertWorker();
    }

    @Test
    void createsQueriesAnswersAndFinishesOneWorkerAnalysis() throws Exception {
        String token = login(HR_A_EMAIL);
        HttpResponse<String> created = post(
                "/api/v1/ai-runs",
                """
                {"instruction":"응웬반A 체류연장 준비해줘"}
                """,
                token,
                "airun-demo-001"
        );

        assertThat(created.statusCode()).isEqualTo(202);
        UUID aiRunId = UUID.fromString(JsonPath.read(created.body(), "$.ai_run_id"));
        assertThat(JsonPath.<String>read(created.body(), "$.analysis_outcome"))
                .isEqualTo("NEEDS_INFO");
        assertThat(JsonPath.<String>read(created.body(), "$.detected_intent"))
                .isEqualTo("EXPIRY_RENEWAL");
        assertThat(JsonPath.<String>read(created.body(), "$.instruction"))
                .isEqualTo("응웬반A 체류연장 준비해줘");
        assertThat(JsonPath.<List<String>>read(created.body(), "$.questions[*].slot_key"))
                .containsExactly("due_at");
        assertThat(JsonPath.<Number>read(created.body(), "$.attempt_count").intValue())
                .isEqualTo(2);
        long version = JsonPath.<Number>read(created.body(), "$.version").longValue();

        HttpResponse<String> detail = get("/api/v1/ai-runs/" + aiRunId, token);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(detail.body(), "$.status")).isEqualTo("SUCCEEDED");

        HttpResponse<String> answered = post(
                "/api/v1/ai-runs/" + aiRunId + "/answers",
                """
                {"expected_version":%d,"answers":{"due_at":"2026-08-31"}}
                """.formatted(version),
                token,
                null
        );
        assertThat(answered.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<String>read(answered.body(), "$.analysis_outcome"))
                .isEqualTo("REVIEW_REQUIRED");
        assertThat(JsonPath.<List<String>>read(answered.body(), "$.candidates[*].workflow_id"))
                .containsExactly("WF-STY-001");
        assertThat(JsonPath.<Number>read(answered.body(), "$.attempt_count").intValue())
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_attempt WHERE ai_run_id = ?",
                Integer.class,
                aiRunId
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                "SELECT action FROM audit_event WHERE target_id = ? ORDER BY created_at",
                String.class,
                aiRunId
        )).containsExactly("AI_RUN_CREATED", "AI_RUN_ANSWERS_SUBMITTED");
    }

    @Test
    void replaysSafeOrderedSseEventsAndEnforcesTenantScope() throws Exception {
        String tokenA = login(HR_A_EMAIL);
        String tokenB = login(HR_B_EMAIL);
        HttpResponse<String> created = post(
                "/api/v1/ai-runs",
                """
                {"instruction":"응웬반A 체류연장 준비해줘"}
                """,
                tokenA,
                "airun-sse-001"
        );
        UUID aiRunId = UUID.fromString(JsonPath.read(created.body(), "$.ai_run_id"));
        long currentVersion = JsonPath.<Number>read(created.body(), "$.version").longValue();

        HttpResponse<String> stream = getEvents(aiRunId, tokenA, null);
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow())
                .startsWith("text/event-stream");
        assertThat(stream.body())
                .contains("id:0", "event:RUN_STARTED", "event:SLOT_CHECKING", "event:NEEDS_INFO")
                .doesNotContain("응웬반A 체류연장 준비해줘", "analysis_input", "prompt", "provider");

        HttpResponse<String> alreadyConsumed = getEvents(aiRunId, tokenA, Long.toString(currentVersion));
        assertThat(alreadyConsumed.statusCode()).isEqualTo(200);
        assertThat(alreadyConsumed.body()).isEmpty();

        assertThat(getEvents(aiRunId, tokenB, null).statusCode()).isEqualTo(404);
        assertThat(getEvents(aiRunId, tokenA, "not-a-number").statusCode()).isEqualTo(400);
    }

    @Test
    void allowsLastEventIdHeaderInCorsPreflight() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        uri("/api/v1/ai-runs/" + UUID.randomUUID() + "/events")
                )
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,last-event-id")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow())
                .containsIgnoringCase("authorization")
                .containsIgnoringCase("last-event-id");
    }

    @Test
    void idempotencyAndCompanyIsolationAreEnforced() throws Exception {
        String tokenA = login(HR_A_EMAIL);
        String tokenB = login(HR_B_EMAIL);
        String body = """
                {"instruction":"응웬반A 체류연장 준비해줘"}
                """;
        HttpResponse<String> first = post(
                "/api/v1/ai-runs",
                body,
                tokenA,
                "airun-demo-duplicate"
        );
        HttpResponse<String> repeated = post(
                "/api/v1/ai-runs",
                body,
                tokenA,
                "airun-demo-duplicate"
        );
        UUID aiRunId = UUID.fromString(JsonPath.read(first.body(), "$.ai_run_id"));

        assertThat(repeated.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<String>read(repeated.body(), "$.ai_run_id"))
                .isEqualTo(aiRunId.toString());
        assertThat(runtimeCalls).hasValue(2);
        assertThat(get("/api/v1/ai-runs/" + aiRunId, tokenB).statusCode()).isEqualTo(404);

        HttpResponse<String> conflict = post(
                "/api/v1/ai-runs",
                """
                {"instruction":"다른 요청"}
                """,
                tokenA,
                "airun-demo-duplicate"
        );
        assertThat(conflict.statusCode()).isEqualTo(409);
    }

    @Test
    void acceptedCandidateCreatesOneCaseAndThreeTasksIdempotently() throws Exception {
        reset(runtimeClient);
        runtimeCalls.set(0);
        when(runtimeClient.analyze(any(), any())).thenAnswer(invocation -> {
            AiAnalysisRequest request = invocation.getArgument(0);
            return directReviewResponse(request, runtimeCalls.incrementAndGet());
        });
        String tokenA = login(HR_A_EMAIL);
        String tokenB = login(HR_B_EMAIL);
        HttpResponse<String> reviewed = post(
                "/api/v1/ai-runs",
                """
                {"instruction":"응웬반A 체류연장 준비해줘"}
                """,
                tokenA,
                "airun-decision-run"
        );
        assertThat(reviewed.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<String>read(reviewed.body(), "$.analysis_outcome"))
                .isEqualTo("REVIEW_REQUIRED");
        assertThat(runtimeCalls).hasValue(2);
        UUID aiRunId = UUID.fromString(JsonPath.read(reviewed.body(), "$.ai_run_id"));
        UUID candidateId = UUID.fromString(JsonPath.read(
                reviewed.body(),
                "$.candidates[0].candidate_id"
        ));
        long expectedVersion = JsonPath.<Number>read(reviewed.body(), "$.version").longValue();
        assertThat(JsonPath.<String>read(reviewed.body(), "$.detected_intent"))
                .isEqualTo("EXPIRY_RENEWAL");

        String decisionBody = """
                {
                  "expected_run_version":%d,
                  "decisions":[{"candidate_id":"%s","action":"ACCEPT"}]
                }
                """.formatted(expectedVersion, candidateId);
        HttpResponse<String> first = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                decisionBody,
                tokenA,
                "candidate-decision-001"
        );
        HttpResponse<String> repeated = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                decisionBody,
                tokenA,
                "candidate-decision-001"
        );

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(repeated.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(repeated.body(), "$.decision_batch_id"))
                .isEqualTo(JsonPath.read(first.body(), "$.decision_batch_id"));
        UUID caseId = UUID.fromString(JsonPath.read(first.body(), "$.case_id"));
        assertThat(JsonPath.<List<String>>read(first.body(), "$.task_ids")).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_case WHERE case_id = ? AND company_id = ?",
                Integer.class,
                caseId,
                COMPANY_A
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT task_type
                FROM task
                WHERE case_id = ? AND company_id = ?
                ORDER BY CASE task_type
                    WHEN 'RECONTRACT' THEN 1
                    WHEN 'STAY_PERIOD_EXTENSION' THEN 2
                    ELSE 3
                END
                """,
                String.class,
                caseId,
                COMPANY_A
        )).containsExactly(
                "RECONTRACT",
                "STAY_PERIOD_EXTENSION",
                "EMPLOYMENT_PERIOD_EXTENSION"
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE case_id = ? AND source = 'AI_CANDIDATE'",
                Integer.class,
                caseId
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                "SELECT due_date FROM task WHERE case_id = ? ORDER BY task_id",
                LocalDate.class,
                caseId
        )).containsOnly(LocalDate.of(2026, 9, 30));
        String snapshot = jdbcTemplate.queryForObject(
                "SELECT workflow_snapshot_json FROM workflow_case WHERE case_id = ?",
                String.class,
                caseId
        );
        assertThat(JsonPath.<List<Integer>>read(snapshot, "$.steps[*].order"))
                .containsExactly(1, 2, 3);
        assertThat(JsonPath.<List<String>>read(snapshot, "$.steps[*].task_type"))
                .containsExactly(
                        "RECONTRACT",
                        "STAY_PERIOD_EXTENSION",
                        "EMPLOYMENT_PERIOD_EXTENSION"
                );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_candidate_decision_batch WHERE ai_run_id = ?",
                Integer.class,
                aiRunId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_candidate_decision_task",
                Integer.class
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                "SELECT action FROM audit_event WHERE target_id = ? ORDER BY created_at",
                String.class,
                aiRunId
        )).containsExactly(
                "AI_RUN_CREATED",
                "AI_RUN_CANDIDATES_DECIDED"
        );

        HttpResponse<String> reusedKeyWithDifferentPayload = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                """
                {
                  "expected_run_version":%d,
                  "decisions":[{"candidate_id":"%s","action":"DISCARD"}]
                }
                """.formatted(expectedVersion, candidateId),
                tokenA,
                "candidate-decision-001"
        );
        assertThat(reusedKeyWithDifferentPayload.statusCode()).isEqualTo(409);

        long decidedVersion = JsonPath.<Number>read(first.body(), "$.run_version").longValue();
        HttpResponse<String> alreadyDecided = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                """
                {
                  "expected_run_version":%d,
                  "decisions":[{"candidate_id":"%s","action":"ACCEPT"}]
                }
                """.formatted(decidedVersion, candidateId),
                tokenA,
                "candidate-decision-002"
        );
        assertThat(alreadyDecided.statusCode()).isEqualTo(409);

        HttpResponse<String> otherCompany = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                decisionBody,
                tokenB,
                "candidate-decision-other-company"
        );
        assertThat(otherCompany.statusCode()).isEqualTo(404);
    }

    private AiAnalysisResponse scriptedResponse(AiAnalysisRequest request, int call) {
        if (call == 1) {
            return new AiAnalysisResponse(
                    request.requestId(),
                    AiAnalysisOutcome.CONTEXT_REQUIRED,
                    new AiContextRequirement(
                            "EXPIRY_RENEWAL",
                            new BigDecimal("0.96"),
                            "응웬반A",
                            Map.of(),
                            List.of("worker_id", "stay_expiry_date", "due_at")
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    versions(),
                    1,
                    30
            );
        }
        if (call == 2) {
            return new AiAnalysisResponse(
                    request.requestId(),
                    AiAnalysisOutcome.NEEDS_INFO,
                    null,
                    List.of(new AiQuestion("due_at", "신청 목표일을 입력해 주세요.")),
                    List.of(),
                    List.of(),
                    versions(),
                    1,
                    20
            );
        }
        return new AiAnalysisResponse(
                request.requestId(),
                AiAnalysisOutcome.REVIEW_REQUIRED,
                null,
                List.of(),
                List.of(new AiCandidate(
                        "candidate-1",
                        WORKER_A,
                        "WF-STY-001",
                        Map.of("due_at", "2026-08-31"),
                        List.of(),
                        new BigDecimal("0.93")
                )),
                List.of(),
                versions(),
                1,
                25
        );
    }

    private AiAnalysisResponse directReviewResponse(AiAnalysisRequest request, int call) {
        if (call == 1) {
            return new AiAnalysisResponse(
                    request.requestId(),
                    AiAnalysisOutcome.CONTEXT_REQUIRED,
                    new AiContextRequirement(
                            "EXPIRY_RENEWAL",
                            new BigDecimal("0.96"),
                            "응웬반A",
                            Map.of(),
                            List.of("worker_id", "stay_expiry_date")
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    versions(),
                    1,
                    30
            );
        }
        return new AiAnalysisResponse(
                request.requestId(),
                AiAnalysisOutcome.REVIEW_REQUIRED,
                null,
                List.of(),
                List.of(new AiCandidate(
                        "candidate-1",
                        WORKER_A,
                        "WF-STY-001",
                        Map.of(
                                "worker_id", WORKER_A.toString(),
                                "stay_expiry_date", "2026-09-30"
                        ),
                        List.of(),
                        new BigDecimal("0.93")
                )),
                List.of(),
                versions(),
                1,
                25
        );
    }

    private AiRuntimeVersions versions() {
        return new AiRuntimeVersions(
                "agent-demo-1",
                "fixture",
                "fixture-model",
                "1",
                "prompt-demo-1",
                "context-demo-1",
                "0.2.0",
                "1.0.0"
        );
    }

    private String login(String email) throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/auth/login",
                """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD),
                null,
                null
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getEvents(UUID aiRunId, String token, String lastEventId)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        uri("/api/v1/ai-runs/" + aiRunId + "/events")
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "text/event-stream")
                .GET();
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(
            String path,
            String body,
            String token,
            String idempotencyKey
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void insertCompany(UUID companyId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                companyId,
                name
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
                userId,
                companyId,
                email,
                email,
                passwordHash
        );
    }

    private void insertWorker() {
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, nationality_code, preferred_language,
                    work_status, stay_expiry_date, contract_start_date, contract_end_date,
                    created_at, updated_at, version
                ) VALUES (?, ?, '응웬반A', 'VN', 'vi', 'ACTIVE', ?, ?, ?,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                WORKER_A,
                COMPANY_A,
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
    }
}
