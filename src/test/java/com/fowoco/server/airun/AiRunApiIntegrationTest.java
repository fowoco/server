package com.fowoco.server.airun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiCandidate;
import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
import com.fowoco.server.aiintegration.application.model.AiQuestion;
import com.fowoco.server.aiintegration.application.model.AiRuntimeVersions;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiRunApiIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("82000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A = UUID.fromString("83000000-0000-0000-0000-000000000001");
    private static final UUID PASSPORT_A = UUID.fromString("84000000-0000-0000-0000-000000000001");
    private static final UUID ARC_A = UUID.fromString("84000000-0000-0000-0000-000000000002");
    private static final String HR_A_EMAIL = "airun.hr.a@example.com";
    private static final String HR_B_EMAIL = "airun.hr.b@example.com";
    private static final String PASSWORD = "Test-password-1!";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

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
        insertIdentityDocument(PASSPORT_A, "PASSPORT_COPY", "VERIFIED");
        insertIdentityDocument(ARC_A, "ARC", "VERIFIED");
    }

    @Test
    void createsQueriesAnswersAndFinishesOneWorkerAnalysis() throws Exception {
        long planBefore = stageCount("PLAN", "PLAN_RUNTIME_CALL", "SUCCESS");
        long slotBefore = stageCount("ANALYZE", "SLOT_RESOLUTION", "SUCCESS");
        long analyzeBefore = stageCount("ANALYZE", "ANALYZE_RUNTIME_CALL", "SUCCESS");
        double reviewRequiredBefore = outcomeCount("REVIEW_REQUIRED");
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
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("QUEUED");
        assertThat(JsonPath.<String>read(created.body(), "$.analysis_outcome")).isNull();
        assertThat(JsonPath.<String>read(created.body(), "$.instruction"))
                .isEqualTo("응웬반A 체류연장 준비해줘");
        assertThat(JsonPath.<Number>read(created.body(), "$.attempt_count").intValue())
                .isEqualTo(1);

        HttpResponse<String> detail = awaitRun(aiRunId, token, "NEEDS_INFO", 2);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(detail.body(), "$.status")).isEqualTo("SUCCEEDED");
        assertThat(JsonPath.<String>read(detail.body(), "$.detected_intent"))
                .isEqualTo("EXPIRY_RENEWAL");
        assertThat(JsonPath.<List<String>>read(detail.body(), "$.questions[*].slot_key"))
                .containsExactly("due_at");
        long version = JsonPath.<Number>read(detail.body(), "$.version").longValue();

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
        assertThat((Object) JsonPath.read(answered.body(), "$.candidates[0].confidence")).isNull();
        assertThat(JsonPath.<Number>read(answered.body(), "$.attempt_count").intValue())
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_attempt WHERE ai_run_id = ?",
                Integer.class,
                aiRunId
        )).isEqualTo(3);
        String analyzeInputJson = jdbcTemplate.queryForObject(
                """
                SELECT analysis_input_json
                FROM ai_attempt
                WHERE ai_run_id = ? AND sequence_no = 2
                """,
                String.class,
                aiRunId
        );
        AnalysisInput persistedAnalyzeInput = objectMapper.readValue(analyzeInputJson, AnalysisInput.class);
        assertThat(persistedAnalyzeInput.plannedIntentDecision().detectedIntent())
                .isEqualTo("EXPIRY_RENEWAL");
        assertThat(persistedAnalyzeInput.plannedIntentDecision().workflowId())
                .isEqualTo("WF-STY-001");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT model_version FROM ai_attempt WHERE ai_run_id = ? AND sequence_no = 1",
                String.class,
                aiRunId
        )).isEqualTo("1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT prompt_version FROM ai_attempt WHERE ai_run_id = ? AND sequence_no = 1",
                String.class,
                aiRunId
        )).isEqualTo("prompt-demo-1");
        assertThat(jdbcTemplate.queryForList(
                "SELECT action FROM audit_event WHERE target_id = ? ORDER BY created_at",
                String.class,
                aiRunId
        )).containsExactly("AI_RUN_CREATED", "AI_RUN_ANSWERS_SUBMITTED");
        ArgumentCaptor<AiAnalysisRequest> requestCaptor = ArgumentCaptor.forClass(AiAnalysisRequest.class);
        verify(runtimeClient, atLeast(3)).analyze(requestCaptor.capture(), any());
        assertThat(requestCaptor.getAllValues())
                .allSatisfy(request -> assertThat(request.deadlineMs()).isEqualTo(240_000L));
        assertThat(stageCount("PLAN", "PLAN_RUNTIME_CALL", "SUCCESS") - planBefore)
                .isEqualTo(1);
        assertThat(stageCount("ANALYZE", "SLOT_RESOLUTION", "SUCCESS") - slotBefore)
                .isEqualTo(1);
        assertThat(stageCount("ANALYZE", "ANALYZE_RUNTIME_CALL", "SUCCESS") - analyzeBefore)
                .isEqualTo(2);
        assertThat(outcomeCount("REVIEW_REQUIRED") - reviewRequiredBefore)
                .isEqualTo(1.0);
    }

    @Test
    void finishesOutOfScopeAfterPlanWithoutResolvingSlotsOrCallingAnalyze() throws Exception {
        long slotBefore = stageCount("ANALYZE", "SLOT_RESOLUTION", "SUCCESS");
        long analyzeBefore = stageCount("ANALYZE", "ANALYZE_RUNTIME_CALL", "SUCCESS");
        double outOfScopeBefore = outcomeCount("OUT_OF_SCOPE");
        reset(runtimeClient);
        runtimeCalls.set(0);
        when(runtimeClient.analyze(any(), any())).thenAnswer(invocation -> {
            AiAnalysisRequest request = invocation.getArgument(0);
            runtimeCalls.incrementAndGet();
            return new AiAnalysisResponse(
                    request.requestId(),
                    AiAnalysisOutcome.OUT_OF_SCOPE,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    versions(),
                    1,
                    15
            );
        });

        String token = login(HR_A_EMAIL);
        HttpResponse<String> created = post(
                "/api/v1/ai-runs",
                """
                {"instruction":"오늘 날씨 어때?"}
                """,
                token,
                "airun-out-of-scope"
        );

        assertThat(created.statusCode()).isEqualTo(202);
        UUID aiRunId = UUID.fromString(JsonPath.read(created.body(), "$.ai_run_id"));
        HttpResponse<String> detail = awaitRun(aiRunId, token, "OUT_OF_SCOPE", 1);

        assertThat(JsonPath.<String>read(detail.body(), "$.status")).isEqualTo("SUCCEEDED");
        assertThat((Object) JsonPath.read(detail.body(), "$.detected_intent")).isNull();
        assertThat(runtimeCalls).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_attempt WHERE ai_run_id = ? AND phase = 'ANALYZE'",
                Integer.class,
                aiRunId
        )).isZero();

        HttpResponse<String> events = getEvents(aiRunId, token, null);
        assertThat(events.statusCode()).isEqualTo(200);
        assertThat(events.body())
                .contains("event:COMPLETED", "\"analysis_outcome\":\"OUT_OF_SCOPE\"")
                .doesNotContain("event:SLOT_CHECKING");
        assertThat(stageCount("ANALYZE", "SLOT_RESOLUTION", "SUCCESS"))
                .isEqualTo(slotBefore);
        assertThat(stageCount("ANALYZE", "ANALYZE_RUNTIME_CALL", "SUCCESS"))
                .isEqualTo(analyzeBefore);
        assertThat(outcomeCount("OUT_OF_SCOPE") - outOfScopeBefore)
                .isEqualTo(1.0);
    }

    private long stageCount(String phase, String stage, String status) {
        Timer timer = meterRegistry.find("fowoco.ai.pipeline.stage")
                .tags("phase", phase, "stage", stage, "status", status)
                .timer();
        return timer == null ? 0L : timer.count();
    }

    private double outcomeCount(String outcome) {
        var counter = meterRegistry.find("fowoco.ai.analysis.outcomes")
                .tag("outcome", outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void replaysSafeOrderedSseEventsAndEnforcesTenantScope() throws Exception {
        CountDownLatch planStarted = new CountDownLatch(1);
        CountDownLatch releasePlan = new CountDownLatch(1);
        reset(runtimeClient);
        runtimeCalls.set(0);
        when(runtimeClient.analyze(any(), any())).thenAnswer(invocation -> {
            int call = runtimeCalls.incrementAndGet();
            if (call == 1) {
                planStarted.countDown();
                if (!releasePlan.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test did not release delayed PLAN call");
                }
            }
            return scriptedResponse(invocation.getArgument(0), call);
        });
        String tokenA = login(HR_A_EMAIL);
        String tokenB = login(HR_B_EMAIL);
        UUID aiRunId;
        String streamBody;
        try {
            HttpResponse<String> created = post(
                    "/api/v1/ai-runs",
                    """
                    {"instruction":"응웬반A 체류연장 준비해줘"}
                    """,
                    tokenA,
                    "airun-sse-001"
            );
            assertThat(created.statusCode()).isEqualTo(202);
            assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("QUEUED");
            aiRunId = UUID.fromString(JsonPath.read(created.body(), "$.ai_run_id"));
            assertThat(planStarted.await(5, TimeUnit.SECONDS)).isTrue();

            HttpResponse<InputStream> stream = openEventStream(aiRunId, tokenA);
            assertThat(stream.statusCode()).isEqualTo(200);
            assertThat(stream.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow())
                    .startsWith("text/event-stream");
            releasePlan.countDown();
            streamBody = new String(stream.body().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            releasePlan.countDown();
        }
        assertThat(streamBody)
                .contains("event:RUN_QUEUED", "event:RUN_STARTED", "event:SLOT_CHECKING", "event:NEEDS_INFO")
                .doesNotContain("응웬반A 체류연장 준비해줘", "analysis_input", "prompt", "provider");

        HttpResponse<String> detail = awaitRun(aiRunId, tokenA, "NEEDS_INFO", 2);
        long currentVersion = JsonPath.<Number>read(detail.body(), "$.version").longValue();

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
        awaitRun(aiRunId, tokenA, "NEEDS_INFO", 2);
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
        UUID aiRunId = UUID.fromString(JsonPath.read(reviewed.body(), "$.ai_run_id"));
        HttpResponse<String> detail = awaitRun(aiRunId, tokenA, "REVIEW_REQUIRED", 2);
        assertThat(runtimeCalls).hasValue(2);
        UUID candidateId = UUID.fromString(JsonPath.read(
                detail.body(),
                "$.candidates[0].candidate_id"
        ));
        long expectedVersion = JsonPath.<Number>read(detail.body(), "$.version").longValue();
        assertThat(JsonPath.<String>read(detail.body(), "$.detected_intent"))
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
        )).containsOnly(LocalDate.of(2026, 8, 31));
        String snapshot = jdbcTemplate.queryForObject(
                "SELECT workflow_snapshot_json FROM workflow_case WHERE case_id = ?",
                String.class,
                caseId
        );
        assertThat(JsonPath.<List<Integer>>read(snapshot, "$.steps[*].order"))
                .containsExactly(1, 3, 4);
        assertThat(JsonPath.<List<String>>read(snapshot, "$.steps[*].task_type"))
                .containsExactly(
                        "RECONTRACT",
                        "EMPLOYMENT_PERIOD_EXTENSION",
                        "STAY_PERIOD_EXTENSION"
                );
        assertThat(JsonPath.<String>read(snapshot, "$.case_template_id"))
                .isEqualTo("CASE-EXPIRY-RENEWAL-001");
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT label
                FROM task_checklist_item
                WHERE task_id = (
                    SELECT task_id FROM task
                    WHERE case_id = ? AND task_type = 'RECONTRACT'
                )
                ORDER BY item_code
                """,
                String.class,
                caseId
        )).contains(
                "회사의 재계약 의사 확인",
                "근로자의 계속 근무 의사 확인",
                "최신 표준근로계약서 초안 검토"
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

    @Test
    void missingArcCreatesOneDraftDocumentRequestInTheRenewalCase() throws Exception {
        jdbcTemplate.update(
                "UPDATE worker_document SET submission_status = 'MISSING' WHERE worker_document_id = ?",
                ARC_A
        );
        reset(runtimeClient);
        runtimeCalls.set(0);
        when(runtimeClient.analyze(any(), any())).thenAnswer(invocation -> directReviewResponse(
                invocation.getArgument(0),
                runtimeCalls.incrementAndGet()
        ));
        String token = login(HR_A_EMAIL);
        HttpResponse<String> created = post(
                "/api/v1/ai-runs",
                """
                {"instruction":"응웬반A 체류연장 준비해줘"}
                """,
                token,
                "airun-missing-arc"
        );
        UUID aiRunId = UUID.fromString(JsonPath.read(created.body(), "$.ai_run_id"));
        HttpResponse<String> detail = awaitRun(aiRunId, token, "REVIEW_REQUIRED", 2);
        UUID candidateId = UUID.fromString(JsonPath.read(detail.body(), "$.candidates[0].candidate_id"));
        long expectedVersion = JsonPath.<Number>read(detail.body(), "$.version").longValue();
        String decisionBody = """
                {
                  "expected_run_version":%d,
                  "decisions":[{"candidate_id":"%s","action":"ACCEPT"}]
                }
                """.formatted(expectedVersion, candidateId);

        HttpResponse<String> decided = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                decisionBody,
                token,
                "candidate-missing-arc"
        );
        HttpResponse<String> repeated = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                decisionBody,
                token,
                "candidate-missing-arc"
        );

        assertThat(decided.statusCode()).isEqualTo(200);
        assertThat(repeated.statusCode()).isEqualTo(200);
        UUID caseId = UUID.fromString(JsonPath.read(decided.body(), "$.case_id"));
        assertThat(JsonPath.<List<String>>read(decided.body(), "$.task_ids")).hasSize(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE case_id = ? AND task_type = 'DOCUMENT_REQUEST'",
                Integer.class,
                caseId
        )).isEqualTo(1);
        Map<String, Object> requestTask = jdbcTemplate.queryForMap(
                """
                SELECT workflow_id, status, business_data_json
                FROM task
                WHERE case_id = ? AND task_type = 'DOCUMENT_REQUEST'
                """,
                caseId
        );
        assertThat(requestTask.get("workflow_id")).isEqualTo("WF-DOC-001");
        assertThat(requestTask.get("status")).isEqualTo("DRAFT");
        assertThat((String) requestTask.get("business_data_json"))
                .contains("ARC", "SECURE_LINK")
                .doesNotContain("PASSPORT_COPY");
        String snapshot = jdbcTemplate.queryForObject(
                "SELECT workflow_snapshot_json FROM workflow_case WHERE case_id = ?",
                String.class,
                caseId
        );
        assertThat(JsonPath.<List<Integer>>read(snapshot, "$.steps[*].order"))
                .containsExactly(1, 2, 3, 4);
        assertThat(JsonPath.<List<String>>read(
                snapshot,
                "$.steps[2].required_conditions.depends_on_task_ids"
        )).hasSize(2);
    }

    @Test
    void twoMissingIdentityDocumentsAreCombinedIntoOneRequestTask() throws Exception {
        jdbcTemplate.update(
                "UPDATE worker_document SET submission_status = 'MISSING' WHERE worker_id = ?",
                WORKER_A
        );
        reset(runtimeClient);
        runtimeCalls.set(0);
        when(runtimeClient.analyze(any(), any())).thenAnswer(invocation -> directReviewResponse(
                invocation.getArgument(0),
                runtimeCalls.incrementAndGet()
        ));
        String token = login(HR_A_EMAIL);
        HttpResponse<String> created = post(
                "/api/v1/ai-runs",
                """
                {"instruction":"응웬반A 체류연장 준비해줘"}
                """,
                token,
                "airun-missing-both-documents"
        );
        UUID aiRunId = UUID.fromString(JsonPath.read(created.body(), "$.ai_run_id"));
        HttpResponse<String> detail = awaitRun(aiRunId, token, "REVIEW_REQUIRED", 2);
        UUID candidateId = UUID.fromString(JsonPath.read(detail.body(), "$.candidates[0].candidate_id"));
        long expectedVersion = JsonPath.<Number>read(detail.body(), "$.version").longValue();

        HttpResponse<String> decided = post(
                "/api/v1/ai-runs/" + aiRunId + "/candidate-decisions",
                """
                {
                  "expected_run_version":%d,
                  "decisions":[{"candidate_id":"%s","action":"ACCEPT"}]
                }
                """.formatted(expectedVersion, candidateId),
                token,
                "candidate-missing-both-documents"
        );

        assertThat(decided.statusCode()).isEqualTo(200);
        UUID caseId = UUID.fromString(JsonPath.read(decided.body(), "$.case_id"));
        assertThat(JsonPath.<List<String>>read(decided.body(), "$.task_ids")).hasSize(4);
        String businessData = jdbcTemplate.queryForObject(
                """
                SELECT business_data_json
                FROM task
                WHERE case_id = ? AND task_type = 'DOCUMENT_REQUEST'
                """,
                String.class,
                caseId
        );
        assertThat(businessData).contains("PASSPORT_COPY", "ARC", "SECURE_LINK");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE case_id = ? AND task_type = 'DOCUMENT_REQUEST'",
                Integer.class,
                caseId
        )).isEqualTo(1);
    }

    private AiAnalysisResponse scriptedResponse(AiAnalysisRequest request, int call) {
        if (call == 1) {
            return new AiAnalysisResponse(
                    request.requestId(),
                    AiAnalysisOutcome.CONTEXT_REQUIRED,
                    new AiContextRequirement(
                            "EXPIRY_RENEWAL",
                            null,
                            "응웬반A",
                            Map.of(),
                            List.of(
                                    "worker_id",
                                    "stay_expiry_date",
                                    "passport_status",
                                    "arc_status",
                                    "due_at"
                            ),
                            "WF-STY-001",
                            "체류연장 준비",
                            AiConfidenceSource.UNAVAILABLE,
                            new BigDecimal("0.3088")
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
                        null
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
                            null,
                            "응웬반A",
                            Map.of(),
                            List.of(
                                    "worker_id",
                                    "stay_expiry_date",
                                    "passport_status",
                                    "arc_status"
                            ),
                            "WF-STY-001",
                            "체류연장 준비",
                            AiConfidenceSource.UNAVAILABLE,
                            new BigDecimal("0.3088")
                    ),
                    List.of(),
                    List.of(),
                    List.of(),
                    versions(),
                    1,
                    30
            );
        }
        Map<String, String> extractedSlots = new LinkedHashMap<>(
                request.analysisInput().workers().get(0).requestedFields()
        );
        extractedSlots.put("due_at", "2026-08-31T18:00");
        return new AiAnalysisResponse(
                request.requestId(),
                AiAnalysisOutcome.REVIEW_REQUIRED,
                null,
                List.of(),
                List.of(new AiCandidate(
                        "candidate-1",
                        WORKER_A,
                        "WF-STY-001",
                        extractedSlots,
                        List.of(),
                        null
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
                "0.3.1",
                "1.1.0"
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

    private HttpResponse<String> awaitRun(
            UUID aiRunId,
            String token,
            String expectedOutcome,
            int expectedAttemptCount
    ) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        HttpResponse<String> latest = null;
        while (System.nanoTime() < deadline) {
            latest = get("/api/v1/ai-runs/" + aiRunId, token);
            String outcome = JsonPath.read(latest.body(), "$.analysis_outcome");
            int attemptCount = JsonPath.<Number>read(latest.body(), "$.attempt_count").intValue();
            if (expectedOutcome.equals(outcome) && attemptCount == expectedAttemptCount) {
                return latest;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("AI Run did not reach expected state: "
                + (latest == null ? "no response" : latest.body()));
    }

    private HttpResponse<InputStream> openEventStream(UUID aiRunId, String token)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        uri("/api/v1/ai-runs/" + aiRunId + "/events")
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "text/event-stream")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
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

    private void insertIdentityDocument(UUID documentId, String documentType, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO worker_document (
                    worker_document_id, worker_id, company_id, document_type, submission_status,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                documentId,
                WORKER_A,
                COMPANY_A,
                documentType,
                status
        );
    }
}
