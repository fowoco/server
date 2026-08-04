package com.fowoco.server.auth.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.demo-seed.enabled=true",
                "app.demo-seed.admin-password=Demo-password-1!"
        }
)
@Import(DemoAuthSeedIntegrationTest.FixedClockConfiguration.class)
class DemoAuthSeedIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TEST_COMPANY_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final UUID TIMELINE_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000002");
    private static final UUID TEST_TASK_ID =
            UUID.fromString("97000000-0000-0000-0000-000000000001");
    private static final UUID REPRESENTATIVE_WORKER_ID =
            UUID.fromString("92000000-0000-0000-0000-000000000006");
    private static final UUID RECONTRACT_CANDIDATE_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000006");
    private static final UUID EMPLOYMENT_EXTENSION_CANDIDATE_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000007");
    private static final UUID PASSPORT_REQUEST_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000008");
    private static final UUID COMPOUND_CASE_ID =
            UUID.fromString("94100000-0000-0000-0000-000000000006");
    private static final String PASSWORD = "Demo-password-1!";
    private static final String DEMO_ADMIN_EMAIL = "demo.admin@example.com";
    private static final String TEST_ADMIN_EMAIL = "test.admin@example.com";
    private static final Instant TEST_NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private static final Map<String, Integer> EXPECTED_DEMO_COUNTS = Map.ofEntries(
            Map.entry("user_account", 20),
            Map.entry("worker", 28),
            Map.entry("task", 24),
            Map.entry("worker_document", 84),
            Map.entry("task_checklist_item", 68),
            Map.entry("approval_request", 13),
            Map.entry("task_transition_history", 52),
            Map.entry("external_submission", 6),
            Map.entry("task_evidence", 10),
            Map.entry("document_request_draft", 5),
            Map.entry("audit_event", 96)
    );
    private static final Map<String, Integer> EXPECTED_TEST_COUNTS = Map.of(
            "user_account", 3,
            "worker", 5,
            "task", 3,
            "worker_document", 8,
            "audit_event", 8
    );

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    @Qualifier("demoAuthSeedRunner")
    private ApplicationRunner demoAuthSeedRunner;

    @Autowired
    @Qualifier("demoWorkerSeedRunner")
    private ApplicationRunner demoWorkerSeedRunner;

    @Autowired
    @Qualifier("demoOperationalSeedRunner")
    private ApplicationRunner demoOperationalSeedRunner;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void enabledSeedIsCompleteIdempotentTenantIsolatedAndReadableThroughApis() throws Exception {
        assertAccountsAndPasswords();
        assertExactCountsAndDistributions();
        assertRelativeDatesAndSafeData();
        assertCompoundDraftScenario();
        assertTaskTimelineInvariants();

        Map<String, Integer> demoCountsBeforeRerun = counts(COMPANY_ID, EXPECTED_DEMO_COUNTS.keySet());
        Map<String, Integer> testCountsBeforeRerun = counts(TEST_COMPANY_ID, EXPECTED_TEST_COUNTS.keySet());
        Map<String, List<Map<String, Object>>> initialSnapshot = seedSnapshot();

        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);
        demoAuthSeedRunner.run(arguments);
        demoWorkerSeedRunner.run(arguments);
        demoOperationalSeedRunner.run(arguments);

        assertThat(counts(COMPANY_ID, EXPECTED_DEMO_COUNTS.keySet()))
                .isEqualTo(demoCountsBeforeRerun)
                .isEqualTo(EXPECTED_DEMO_COUNTS);
        assertThat(counts(TEST_COMPANY_ID, EXPECTED_TEST_COUNTS.keySet()))
                .isEqualTo(testCountsBeforeRerun)
                .isEqualTo(EXPECTED_TEST_COUNTS);
        assertThat(seedSnapshot()).isEqualTo(initialSnapshot);

        assertFrontendRelevantApiResultsAndTenantIsolation();

        mutableClock.advance(Duration.ofDays(1));
        demoAuthSeedRunner.run(arguments);
        demoWorkerSeedRunner.run(arguments);
        demoOperationalSeedRunner.run(arguments);

        assertThat(counts(COMPANY_ID, EXPECTED_DEMO_COUNTS.keySet()))
                .isEqualTo(demoCountsBeforeRerun)
                .isEqualTo(EXPECTED_DEMO_COUNTS);
        assertThat(counts(TEST_COMPANY_ID, EXPECTED_TEST_COUNTS.keySet()))
                .isEqualTo(testCountsBeforeRerun)
                .isEqualTo(EXPECTED_TEST_COUNTS);
        assertThat(seedSnapshot()).isEqualTo(initialSnapshot);
        assertTaskTimelineInvariants();
    }

    private void assertAccountsAndPasswords() {
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE user_id = ? AND company_id = ?",
                String.class,
                ADMIN_USER_ID,
                COMPANY_ID
        );

        assertThat(passwordHash).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();
        assertThat(count("company", COMPANY_ID)).isEqualTo(1);
        assertThat(count("company", TEST_COMPANY_ID)).isEqualTo(1);
        assertThat(distribution("user_account", "role", COMPANY_ID)).containsExactlyInAnyOrderEntriesOf(
                Map.of("ADMIN", 2, "HR", 12, "VIEWER", 6)
        );
        assertThat(distribution("user_account", "role", TEST_COMPANY_ID)).containsExactlyInAnyOrderEntriesOf(
                Map.of("ADMIN", 1, "HR", 1, "VIEWER", 1)
        );
    }

    private void assertExactCountsAndDistributions() {
        assertThat(counts(COMPANY_ID, EXPECTED_DEMO_COUNTS.keySet()))
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_DEMO_COUNTS);
        assertThat(counts(TEST_COMPANY_ID, EXPECTED_TEST_COUNTS.keySet()))
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_TEST_COUNTS);

        assertThat(distribution("task", "task_type", COMPANY_ID)).containsExactlyInAnyOrderEntriesOf(
                Map.of("STAY_PERIOD_EXTENSION", 10, "RECONTRACT", 8, "EMPLOYMENT_PERIOD_EXTENSION", 6)
        );
        assertThat(distribution("task", "status", COMPANY_ID)).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "DRAFT", 3,
                        "NEEDS_INFO", 2,
                        "READY_FOR_REVIEW", 4,
                        "APPROVED", 2,
                        "WAITING_WORKER", 4,
                        "WAITING_EXTERNAL", 3,
                        "COMPLETED", 5,
                        "CANCELLED", 1
                )
        );
        assertThat(distribution("worker_document", "document_type", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("PASSPORT_COPY", 26, "ARC", 28, "CONTRACT", 22, "PERMIT", 8)
                );
        assertThat(distribution("worker_document", "submission_status", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("VERIFIED", 48, "SUBMITTED", 20, "MISSING", 16)
                );
        assertThat(distribution("approval_request", "status", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("PENDING", 4, "APPROVED", 7, "REJECTED", 1, "INVALIDATED", 1)
                );
        assertThat(distribution("audit_event", "actor_type", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("HR_USER", 81, "AI_AGENT", 5, "SYSTEM_RULE", 6, "WORKER_LINK", 4)
                );

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM task_checklist_item checklist
                JOIN task seeded_task ON seeded_task.task_id = checklist.task_id
                WHERE checklist.company_id = ? AND seeded_task.company_id = ?
                """,
                Integer.class,
                COMPANY_ID,
                COMPANY_ID
        )).isEqualTo(68);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT task_id) FROM task_checklist_item WHERE company_id = ?",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(24);
    }

    private void assertRelativeDatesAndSafeData() {
        LocalDate today = LocalDate.now(clock);
        assertThat(countWhere("task", "due_date = ?", COMPANY_ID, today)).isGreaterThanOrEqualTo(1);
        assertThat(countWhere(
                "task",
                "due_date >= ? AND due_date <= ?",
                COMPANY_ID,
                today,
                today.plusDays(7)
        )).isGreaterThan(1);
        List<Instant> completedUpdates = jdbcTemplate.query(
                "SELECT updated_at FROM task WHERE company_id = ? AND status = 'COMPLETED'",
                (resultSet, rowNumber) -> resultSet.getTimestamp("updated_at").toInstant(),
                COMPANY_ID
        );
        assertThat(completedUpdates)
                .hasSize(5)
                .allMatch(updatedAt -> LocalDate.ofInstant(updatedAt, ZoneOffset.UTC).equals(today));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stay_expiry_date FROM worker WHERE worker_id = ? AND company_id = ?",
                LocalDate.class,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).isEqualTo(today.plusDays(45));

        assertThat(countWhere(
                "audit_event",
                "created_at >= ? AND created_at <= ?",
                COMPANY_ID,
                clock.instant().minus(7, ChronoUnit.DAYS),
                clock.instant()
        )).isPositive();
        assertThat(countWhere(
                "audit_event",
                "created_at < ?",
                COMPANY_ID,
                clock.instant().minus(30, ChronoUnit.DAYS)
        )).isPositive();

        assertThat(countWhere("worker_document", "expiry_date < ?", COMPANY_ID, today)).isPositive();
        assertThat(countWhere(
                "worker_document",
                "expiry_date >= ? AND expiry_date <= ?",
                COMPANY_ID,
                today,
                today.plusDays(30)
        )).isPositive();
        assertThat(countWhere(
                "worker_document",
                "expiry_date > ? AND expiry_date <= ?",
                COMPANY_ID,
                today.plusDays(30),
                today.plusDays(90)
        )).isPositive();
        assertThat(countWhere(
                "worker_document",
                "expiry_date > ?",
                COMPANY_ID,
                today.plusDays(90)
        )).isPositive();
        assertThat(countWhere("worker_document", "expiry_date IS NULL", COMPANY_ID)).isPositive();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stored_file", Integer.class)).isZero();
        List<String> businessData = jdbcTemplate.query(
                "SELECT business_data_json FROM task WHERE company_id = ?",
                (resultSet, rowNumber) -> resultSet.getString(1).toLowerCase(),
                COMPANY_ID
        );
        assertThat(businessData).allMatch(json -> List.of(
                "passport_number",
                "arc_number",
                "phone_number",
                "bank_account",
                "password",
                "access_token"
        ).stream().noneMatch(json::contains));
    }

    private void assertCompoundDraftScenario() {
        List<Map<String, Object>> tasks = jdbcTemplate.queryForList(
                "SELECT task_id, case_id, task_type, workflow_id, source, status, business_data_json "
                        + "FROM task WHERE task_id IN (?, ?, ?) ORDER BY task_id",
                RECONTRACT_CANDIDATE_TASK_ID,
                EMPLOYMENT_EXTENSION_CANDIDATE_TASK_ID,
                PASSPORT_REQUEST_TASK_ID
        );
        assertThat(tasks).hasSize(3).allSatisfy(task -> {
            assertThat(task.get("case_id")).isEqualTo(COMPOUND_CASE_ID);
            String businessData = (String) task.get("business_data_json");
            assertThat(JsonPath.<String>read(businessData, "$.demo_scenario"))
                    .isEqualTo("compound-draft-flow-v1");
            assertThat(JsonPath.<String>read(businessData, "$.stay_qualification"))
                    .isEqualTo("E-9");
            assertThat(JsonPath.<Integer>read(businessData, "$.input_summary.required_count"))
                    .isEqualTo(9);
            assertThat(JsonPath.<Integer>read(businessData, "$.input_summary.available_count"))
                    .isEqualTo(7);
        });

        assertThat(tasks.get(0))
                .containsEntry("task_type", "RECONTRACT")
                .containsEntry("workflow_id", "WF-CON-001")
                .containsEntry("source", "AI_CANDIDATE")
                .containsEntry("status", "READY_FOR_REVIEW");
        assertThat(tasks.get(1))
                .containsEntry("task_type", "EMPLOYMENT_PERIOD_EXTENSION")
                .containsEntry("workflow_id", "WF-CON-001")
                .containsEntry("source", "AI_CANDIDATE")
                .containsEntry("status", "DRAFT");
        assertThat(tasks.get(2))
                .containsEntry("task_type", "STAY_PERIOD_EXTENSION")
                .containsEntry("workflow_id", "WF-STY-001")
                .containsEntry("source", "AI_CANDIDATE")
                .containsEntry("status", "WAITING_WORKER");

        Map<String, Map<String, Object>> documentsByType = jdbcTemplate.query(
                "SELECT document_type, submission_status, expiry_date, destination, note "
                        + "FROM worker_document WHERE worker_id = ? AND company_id = ?",
                resultSet -> {
                    Map<String, Map<String, Object>> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        Map<String, Object> document = new LinkedHashMap<>();
                        document.put("submission_status", resultSet.getString("submission_status"));
                        document.put("expiry_date", resultSet.getObject("expiry_date", LocalDate.class));
                        document.put("destination", resultSet.getString("destination"));
                        document.put("note", resultSet.getString("note"));
                        result.put(resultSet.getString("document_type"), document);
                    }
                    return result;
                },
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        );
        assertThat(documentsByType).containsOnlyKeys("PASSPORT_COPY", "ARC", "CONTRACT");
        assertThat(documentsByType.get("PASSPORT_COPY"))
                .containsEntry("submission_status", "MISSING")
                .containsEntry("destination", "근로자 문서 요청");
        assertThat(documentsByType.get("PASSPORT_COPY").get("expiry_date")).isNull();
        assertThat(documentsByType.get("ARC"))
                .containsEntry("submission_status", "VERIFIED");
        assertThat(documentsByType.get("CONTRACT"))
                .containsEntry("submission_status", "VERIFIED");
    }

    private void assertTaskTimelineInvariants() {
        Map<UUID, TaskTimeline> tasks = jdbcTemplate.query(
                "SELECT task_id, status, created_at FROM task WHERE company_id = ?",
                resultSet -> {
                    Map<UUID, TaskTimeline> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        UUID taskId = resultSet.getObject("task_id", UUID.class);
                        result.put(taskId, new TaskTimeline(
                                taskId,
                                resultSet.getString("status"),
                                resultSet.getTimestamp("created_at").toInstant()
                        ));
                    }
                    return result;
                },
                COMPANY_ID
        );
        Map<UUID, List<TransitionTimeline>> transitionsByTask = jdbcTemplate.query(
                "SELECT task_id, from_status, to_status, created_at FROM task_transition_history "
                        + "WHERE company_id = ? ORDER BY task_id, created_at, transition_id",
                resultSet -> {
                    Map<UUID, List<TransitionTimeline>> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        UUID taskId = resultSet.getObject("task_id", UUID.class);
                        result.computeIfAbsent(taskId, ignored -> new java.util.ArrayList<>()).add(
                                new TransitionTimeline(
                                        resultSet.getString("from_status"),
                                        resultSet.getString("to_status"),
                                        resultSet.getTimestamp("created_at").toInstant()
                                )
                        );
                    }
                    return result;
                },
                COMPANY_ID
        );

        assertThat(tasks).hasSize(24);
        tasks.values().forEach(task -> {
            List<TransitionTimeline> transitions = transitionsByTask.getOrDefault(task.taskId(), List.of());
            String expectedFrom = "DRAFT";
            Instant previousAt = task.createdAt();
            for (int index = 0; index < transitions.size(); index++) {
                TransitionTimeline transition = transitions.get(index);
                assertThat(transition.fromStatus()).isEqualTo(expectedFrom);
                assertThat(transition.createdAt()).isAfterOrEqualTo(previousAt);
                if (isTerminal(transition.toStatus())) {
                    assertThat(index).isEqualTo(transitions.size() - 1);
                }
                expectedFrom = transition.toStatus();
                previousAt = transition.createdAt();
            }
            if (!transitions.isEmpty()) {
                assertThat(expectedFrom).isEqualTo(task.status());
            }
        });

        jdbcTemplate.query(
                "SELECT task_id, required, completed, completed_at FROM task_checklist_item "
                        + "WHERE company_id = ?",
                resultSet -> {
                    while (resultSet.next()) {
                        if (!resultSet.getBoolean("required") || !resultSet.getBoolean("completed")) {
                            continue;
                        }
                        UUID taskId = resultSet.getObject("task_id", UUID.class);
                        Instant readyAt = transitionAt(
                                transitionsByTask.getOrDefault(taskId, List.of()),
                                "READY_FOR_REVIEW"
                        );
                        if (readyAt != null) {
                            assertThat(resultSet.getTimestamp("completed_at").toInstant())
                                    .isBeforeOrEqualTo(readyAt);
                        }
                    }
                    return null;
                },
                COMPANY_ID
        );

        jdbcTemplate.query(
                "SELECT task_id, status, requested_at, decided_at, invalidated_at "
                        + "FROM approval_request WHERE company_id = ?",
                resultSet -> {
                    while (resultSet.next()) {
                        UUID taskId = resultSet.getObject("task_id", UUID.class);
                        String status = resultSet.getString("status");
                        Instant requestedAt = resultSet.getTimestamp("requested_at").toInstant();
                        var decidedTimestamp = resultSet.getTimestamp("decided_at");
                        var invalidatedTimestamp = resultSet.getTimestamp("invalidated_at");
                        Instant outcomeAt = "INVALIDATED".equals(status)
                                ? invalidatedTimestamp.toInstant()
                                : decidedTimestamp == null ? null : decidedTimestamp.toInstant();
                        List<TransitionTimeline> transitions =
                                transitionsByTask.getOrDefault(taskId, List.of());
                        Instant readyAt = transitionAt(transitions, "READY_FOR_REVIEW");
                        assertThat(readyAt).isNotNull();
                        assertThat(requestedAt).isAfterOrEqualTo(readyAt);
                        if (outcomeAt != null) {
                            assertThat(outcomeAt).isAfterOrEqualTo(requestedAt);
                        }
                        if ("APPROVED".equals(status)) {
                            assertThat(transitionAt(transitions, "APPROVED"))
                                    .isAfterOrEqualTo(outcomeAt);
                        }
                        if ("REJECTED".equals(status) || "INVALIDATED".equals(status)) {
                            assertThat(transitionAt(transitions, "DRAFT"))
                                    .isAfterOrEqualTo(outcomeAt);
                        }
                    }
                    return null;
                },
                COMPANY_ID
        );

        jdbcTemplate.query(
                "SELECT task_id, submitted_at FROM external_submission WHERE company_id = ?",
                resultSet -> {
                    while (resultSet.next()) {
                        UUID taskId = resultSet.getObject("task_id", UUID.class);
                        Instant submittedAt = resultSet.getTimestamp("submitted_at").toInstant();
                        TaskTimeline task = tasks.get(taskId);
                        List<TransitionTimeline> transitions =
                                transitionsByTask.getOrDefault(taskId, List.of());
                        Instant approvedAt = transitionAt(transitions, "APPROVED");
                        Instant completedAt = transitionAt(transitions, "COMPLETED");
                        assertThat(submittedAt).isAfterOrEqualTo(task.createdAt());
                        if (approvedAt != null) {
                            assertThat(submittedAt).isAfterOrEqualTo(approvedAt);
                        }
                        if (completedAt != null) {
                            assertThat(submittedAt).isBeforeOrEqualTo(completedAt);
                        }
                    }
                    return null;
                },
                COMPANY_ID
        );

        jdbcTemplate.query(
                "SELECT task_id, recorded_at FROM task_evidence WHERE company_id = ?",
                resultSet -> {
                    while (resultSet.next()) {
                        UUID taskId = resultSet.getObject("task_id", UUID.class);
                        Instant recordedAt = resultSet.getTimestamp("recorded_at").toInstant();
                        Instant completedAt = transitionAt(
                                transitionsByTask.getOrDefault(taskId, List.of()),
                                "COMPLETED"
                        );
                        assertThat(recordedAt).isAfterOrEqualTo(tasks.get(taskId).createdAt());
                        assertThat(completedAt).isNotNull().isAfterOrEqualTo(recordedAt);
                    }
                    return null;
                },
                COMPANY_ID
        );
    }

    private Map<String, List<Map<String, Object>>> seedSnapshot() {
        Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
        snapshot.put("worker", snapshotRows(
                "SELECT worker_id, company_id, stay_expiry_date, contract_start_date, "
                        + "contract_end_date, created_at, updated_at, version FROM worker "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, worker_id"
        ));
        snapshot.put("worker_document", snapshotRows(
                "SELECT worker_document_id, company_id, expiry_date, created_at, updated_at, version "
                        + "FROM worker_document WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, worker_document_id"
        ));
        snapshot.put("task", snapshotRows(
                "SELECT task_id, company_id, due_date, business_data_json, critical_fingerprint, "
                        + "content_revision, created_at, updated_at, version FROM task "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, task_id"
        ));
        snapshot.put("task_checklist_item", snapshotRows(
                "SELECT checklist_item_id, company_id, completed_at, created_at, updated_at, version "
                        + "FROM task_checklist_item WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, checklist_item_id"
        ));
        snapshot.put("approval_request", snapshotRows(
                "SELECT approval_request_id, company_id, target_task_version, target_content_revision, "
                        + "approved_task_version, target_fingerprint, requested_at, decided_at, "
                        + "invalidated_at, created_at, updated_at, version FROM approval_request "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, approval_request_id"
        ));
        snapshot.put("task_transition_history", snapshotRows(
                "SELECT transition_id, company_id, created_at FROM task_transition_history "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, transition_id"
        ));
        snapshot.put("external_submission", snapshotRows(
                "SELECT external_submission_id, company_id, submitted_at, created_at "
                        + "FROM external_submission WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, external_submission_id"
        ));
        snapshot.put("task_evidence", snapshotRows(
                "SELECT evidence_id, company_id, recorded_at, created_at FROM task_evidence "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, evidence_id"
        ));
        snapshot.put("document_request_draft", snapshotRows(
                "SELECT draft_id, company_id, created_at, updated_at, version "
                        + "FROM document_request_draft WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, draft_id"
        ));
        snapshot.put("audit_event", snapshotRows(
                "SELECT audit_event_id, company_id, created_at FROM audit_event "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, audit_event_id"
        ));
        return snapshot;
    }

    private List<Map<String, Object>> snapshotRows(String sql) {
        return jdbcTemplate.queryForList(sql, COMPANY_ID, TEST_COMPANY_ID);
    }

    private Instant transitionAt(List<TransitionTimeline> transitions, String toStatus) {
        return transitions.stream()
                .filter(transition -> toStatus.equals(transition.toStatus()))
                .findFirst()
                .map(TransitionTimeline::createdAt)
                .orElse(null);
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }

    private void assertFrontendRelevantApiResultsAndTenantIsolation() throws Exception {
        String demoToken = loginAndGetAccessToken(DEMO_ADMIN_EMAIL);
        String testToken = loginAndGetAccessToken(TEST_ADMIN_EMAIL);
        LocalDate today = LocalDate.now(clock);

        HttpResponse<String> workers = authorizedGet("/api/v1/workers?size=100", demoToken);
        assertOk(workers);
        assertThat(JsonPath.<Number>read(workers.body(), "$.total_elements").intValue()).isEqualTo(28);
        assertThat(JsonPath.<List<?>>read(workers.body(), "$.items")).hasSize(28);

        HttpResponse<String> tasks = authorizedGet("/api/v1/tasks?size=100", demoToken);
        assertOk(tasks);
        assertThat(JsonPath.<Number>read(tasks.body(), "$.total_elements").intValue()).isEqualTo(24);
        List<Map<String, Object>> taskItems = JsonPath.read(tasks.body(), "$.items");
        assertThat(taskItems).hasSize(24);
        assertStatusApiCount(demoToken, "READY_FOR_REVIEW", 4);
        assertStatusApiCount(demoToken, "DRAFT", 3);
        assertThat(taskItems).anyMatch(task -> today.toString().equals(task.get("due_date")));

        HttpResponse<String> dueSoon = authorizedGet(
                "/api/v1/tasks?dueTo=" + today.plusDays(7) + "&size=100",
                demoToken
        );
        assertOk(dueSoon);
        assertThat(JsonPath.<Number>read(dueSoon.body(), "$.total_elements").intValue()).isGreaterThan(1);
        assertThat(taskItems.stream()
                .filter(task -> "COMPLETED".equals(task.get("status")))
                .map(task -> (String) task.get("updated_at"))
                .toList())
                .hasSize(5)
                .allMatch(updatedAt -> updatedAt.startsWith(today.toString()));
        assertWorkerDerivedCategories(taskItems);

        HttpResponse<String> documents = authorizedGet("/api/v1/documents?size=100", demoToken);
        assertOk(documents);
        assertThat(JsonPath.<Number>read(documents.body(), "$.total_elements").intValue()).isEqualTo(84);
        assertDocumentStatusHasData(demoToken, "SUBMITTED");
        assertDocumentStatusHasData(demoToken, "MISSING");
        assertDocumentStatusHasData(demoToken, "VERIFIED");
        HttpResponse<String> expiringDocuments = authorizedGet(
                "/api/v1/documents?expiryBefore=" + today.plusDays(30) + "&size=100",
                demoToken
        );
        assertOk(expiringDocuments);
        assertThat(JsonPath.<Number>read(expiringDocuments.body(), "$.total_elements").intValue()).isPositive();

        HttpResponse<String> detail = authorizedGet("/api/v1/tasks/" + TIMELINE_TASK_ID, demoToken);
        assertOk(detail);
        assertThat(JsonPath.<List<?>>read(detail.body(), "$.checklist_items")).isNotEmpty();
        HttpResponse<String> activities = authorizedGet(
                "/api/v1/tasks/" + TIMELINE_TASK_ID + "/activities",
                demoToken
        );
        assertOk(activities);
        assertThat(JsonPath.<List<?>>read(activities.body(), "$" )).hasSize(3);

        HttpResponse<String> demoAudits = authorizedGet("/api/v1/audit-events?limit=100", demoToken);
        HttpResponse<String> testAudits = authorizedGet("/api/v1/audit-events?limit=100", testToken);
        assertOk(demoAudits);
        assertOk(testAudits);
        assertThat(JsonPath.<List<?>>read(demoAudits.body(), "$.items")).hasSize(96);
        assertThat(JsonPath.<List<?>>read(testAudits.body(), "$.items")).hasSize(8);

        assertTenantApiCount(testToken, "/api/v1/workers?size=100", 5);
        assertTenantApiCount(testToken, "/api/v1/tasks?size=100", 3);
        assertTenantApiCount(testToken, "/api/v1/documents?size=100", 8);
        assertThat(authorizedGet("/api/v1/tasks/" + TEST_TASK_ID, demoToken).statusCode()).isEqualTo(404);
        assertThat(authorizedGet("/api/v1/tasks/" + TIMELINE_TASK_ID, testToken).statusCode()).isEqualTo(404);
    }

    private void assertWorkerDerivedCategories(List<Map<String, Object>> taskItems) {
        Set<String> reviewStatuses = Set.of("READY_FOR_REVIEW", "WAITING_WORKER", "WAITING_EXTERNAL");
        Set<String> aiStatuses = Set.of("DRAFT", "NEEDS_INFO");
        Map<String, Set<String>> statusesByWorker = taskItems.stream().collect(Collectors.groupingBy(
                task -> (String) task.get("worker_id"),
                Collectors.mapping(task -> (String) task.get("status"), Collectors.toSet())
        ));
        Predicate<Set<String>> needsReview = statuses -> statuses.stream().anyMatch(reviewStatuses::contains);
        Predicate<Set<String>> aiSuggested = statuses -> !needsReview.test(statuses)
                && statuses.stream().anyMatch(aiStatuses::contains);

        assertThat(statusesByWorker.values()).anyMatch(needsReview);
        assertThat(statusesByWorker.values()).anyMatch(aiSuggested);
        assertThat(statusesByWorker.values()).anyMatch(statuses ->
                !needsReview.test(statuses) && !aiSuggested.test(statuses));
    }

    private void assertStatusApiCount(String token, String status, int expected) throws Exception {
        HttpResponse<String> response = authorizedGet(
                "/api/v1/tasks?status=" + status + "&size=100",
                token
        );
        assertOk(response);
        assertThat(JsonPath.<Number>read(response.body(), "$.total_elements").intValue())
                .isEqualTo(expected);
    }

    private void assertDocumentStatusHasData(String token, String status) throws Exception {
        HttpResponse<String> response = authorizedGet(
                "/api/v1/documents?status=" + status + "&size=100",
                token
        );
        assertOk(response);
        assertThat(JsonPath.<Number>read(response.body(), "$.total_elements").intValue()).isPositive();
    }

    private void assertTenantApiCount(String token, String path, int expected) throws Exception {
        HttpResponse<String> response = authorizedGet(path, token);
        assertOk(response);
        assertThat(JsonPath.<Number>read(response.body(), "$.total_elements").intValue())
                .isEqualTo(expected);
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)
                ))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertOk(response);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private HttpResponse<String> authorizedGet(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertOk(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Map<String, Integer> counts(UUID companyId, Set<String> tables) {
        Map<String, Integer> result = new LinkedHashMap<>();
        tables.forEach(table -> result.put(table, count(table, companyId)));
        return result;
    }

    private int count(String table, UUID companyId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE company_id = ?",
                Integer.class,
                companyId
        );
    }

    private int countWhere(String table, String predicate, UUID companyId, Object... parameters) {
        Object[] arguments = new Object[parameters.length + 1];
        arguments[0] = companyId;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE company_id = ? AND " + predicate,
                Integer.class,
                arguments
        );
    }

    private Map<String, Integer> distribution(String table, String column, UUID companyId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + column + " AS item_key, COUNT(*) AS item_count FROM " + table
                        + " WHERE company_id = ? GROUP BY " + column,
                companyId
        );
        Map<String, Integer> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(
                row.get("item_key").toString(),
                ((Number) row.get("item_count")).intValue()
        ));
        return result;
    }

    private record TaskTimeline(UUID taskId, String status, Instant createdAt) {
    }

    private record TransitionTimeline(String fromStatus, String toStatus, Instant createdAt) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock fixedClock() {
            return new MutableClock(TEST_NOW, ZoneOffset.UTC);
        }
    }

    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = new AtomicReference<>(instant);
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
