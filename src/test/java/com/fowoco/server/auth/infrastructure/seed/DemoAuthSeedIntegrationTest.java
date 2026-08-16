package com.fowoco.server.auth.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
import com.fowoco.server.airun.application.AiSlotResolutionTransaction;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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
    private static final UUID REPRESENTATIVE_APPROVAL_ID =
            UUID.fromString("94300000-0000-0000-0000-000000000002");
    private static final UUID COMPLETED_STAY_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000020");
    private static final UUID CONTRACT_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000005");
    private static final UUID CONTRACT_WORKER_ID =
            UUID.fromString("92000000-0000-0000-0000-000000000005");
    private static final UUID COMPLETED_STAY_WORKER_ID =
            UUID.fromString("92000000-0000-0000-0000-000000000018");
    private static final UUID PASSPORT_REQUEST_DRAFT_ID =
            UUID.fromString("94700000-0000-0000-0000-000000000002");
    private static final Set<UUID> REPRESENTATIVE_CONTEXT_DOCUMENT_IDS = Set.of(
            UUID.fromString("95000000-0000-0000-0000-000000000016"),
            UUID.fromString("95000000-0000-0000-0000-000000000017")
    );
    private static final Set<UUID> RETIRED_REPRESENTATIVE_DOCUMENT_IDS = Set.of(
            UUID.fromString("95000000-0000-0000-0000-000000000018")
    );
    private static final UUID CONTRACT_FILE_ID =
            UUID.fromString("94800000-0000-0000-0000-000000000001");
    private static final UUID STAY_RECEIPT_FILE_ID =
            UUID.fromString("94800000-0000-0000-0000-000000000002");
    private static final UUID STAY_RESULT_FILE_ID =
            UUID.fromString("94800000-0000-0000-0000-000000000003");
    private static final Path DEMO_FILE_STORAGE_PATH = Path.of(
            System.getProperty("java.io.tmpdir"),
            "fowoco-demo-seed-" + UUID.randomUUID()
    );
    private static final String PASSWORD = "Demo-password-1!";
    private static final String DEMO_ADMIN_EMAIL = "demo.admin@example.com";
    private static final String TEST_ADMIN_EMAIL = "test.admin@example.com";
    private static final Instant TEST_NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private static final Map<String, Integer> EXPECTED_DEMO_COUNTS = Map.ofEntries(
            Map.entry("user_account", 20),
            Map.entry("worker", 28),
            Map.entry("task", 21),
            Map.entry("workflow_case", 21),
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
    private static final Map<String, Integer> EXPECTED_TEST_COUNTS = Map.of(
            "user_account", 3,
            "worker", 5,
            "task", 3,
            "workflow_case", 3,
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
    private AiSlotResolutionTransaction aiSlotResolutionTransaction;

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

    @DynamicPropertySource
    static void demoFileStorageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.local-path", () -> DEMO_FILE_STORAGE_PATH.toString());
    }

    @Test
    void enabledSeedIsCompleteIdempotentTenantIsolatedAndReadableThroughApis() throws Exception {
        assertAccountsAndPasswords();
        assertExactCountsAndDistributions();
        assertRelativeDatesAndSafeData();
        assertGoldenFlowStartState();
        assertGoldenFlowDocumentContext();
        assertShowcaseApprovalAndSubmissionFixtures();
        assertStoredFileFixtures();
        assertTaskTimelineInvariants();
        assertRepairsPartialStoredFileState();

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
        assertStoredFileFixtures();
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
                Map.of("STAY_PERIOD_EXTENSION", 9, "RECONTRACT", 7, "EMPLOYMENT_PERIOD_EXTENSION", 5)
        );
        assertThat(distribution("task", "status", COMPANY_ID)).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "DRAFT", 2,
                        "NEEDS_INFO", 2,
                        "READY_FOR_REVIEW", 3,
                        "APPROVED", 2,
                        "WAITING_WORKER", 3,
                        "WAITING_EXTERNAL", 3,
                        "COMPLETED", 5,
                        "CANCELLED", 1
                )
        );
        assertThat(distribution("worker_document", "document_type", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("PASSPORT_COPY", 26, "ARC", 28, "CONTRACT", 21, "PERMIT", 8)
                );
        assertThat(distribution("worker_document", "submission_status", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("VERIFIED", 47, "SUBMITTED", 20, "MISSING", 16)
                );
        assertThat(distribution("approval_request", "status", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("PENDING", 3, "APPROVED", 10, "REJECTED", 1, "INVALIDATED", 1)
                );
        assertThat(distribution("audit_event", "actor_type", COMPANY_ID))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("HR_USER", 83, "AI_AGENT", 2, "SYSTEM_RULE", 6, "WORKER_LINK", 3)
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
        )).isEqualTo(60);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT task_id) FROM task_checklist_item WHERE company_id = ?",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(21);
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

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ? AND size > 0",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(3);
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

    private void assertGoldenFlowStartState() {
        assertThat(countWhere("worker", "worker_id = ?", COMPANY_ID, REPRESENTATIVE_WORKER_ID))
                .isEqualTo(1);
        assertThat(countWhere("workflow_case", "worker_id = ?", COMPANY_ID, REPRESENTATIVE_WORKER_ID))
                .isZero();
        assertThat(countWhere("task", "worker_id = ?", COMPANY_ID, REPRESENTATIVE_WORKER_ID))
                .isZero();
        assertThat(countWhere("worker_document", "worker_id = ?", COMPANY_ID, REPRESENTATIVE_WORKER_ID))
                .isEqualTo(2);
        assertThat(countWhere("stored_file", "worker_id = ?", COMPANY_ID, REPRESENTATIVE_WORKER_ID))
                .isZero();

        assertThat(jdbcTemplate.queryForList(
                "SELECT task_id FROM task WHERE task_id IN (?, ?, ?) ORDER BY task_id",
                UUID.class,
                RECONTRACT_CANDIDATE_TASK_ID,
                EMPLOYMENT_EXTENSION_CANDIDATE_TASK_ID,
                PASSPORT_REQUEST_TASK_ID
        )).isEmpty();
        assertThat(countWhere("workflow_case", "case_id = ?", COMPANY_ID, COMPOUND_CASE_ID))
                .isZero();
        assertThat(countWhere(
                "approval_request",
                "approval_request_id = ?",
                COMPANY_ID,
                REPRESENTATIVE_APPROVAL_ID
        )).isZero();
        assertThat(countWhere(
                "document_request_draft",
                "draft_id = ?",
                COMPANY_ID,
                PASSPORT_REQUEST_DRAFT_ID
        )).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT worker_document_id FROM worker_document "
                        + "WHERE worker_document_id IN (?, ?)",
                UUID.class,
                REPRESENTATIVE_CONTEXT_DOCUMENT_IDS.toArray()
        )).containsExactlyInAnyOrderElementsOf(REPRESENTATIVE_CONTEXT_DOCUMENT_IDS);
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT document_type, submission_status, expiry_date, task_id, file_id
                FROM worker_document
                WHERE worker_id = ? AND company_id = ?
                ORDER BY document_type
                """,
                REPRESENTATIVE_WORKER_ID,
                COMPANY_ID
        )).satisfiesExactly(
                arc -> {
                    assertThat(arc.get("document_type")).isEqualTo("ARC");
                    assertThat(arc.get("submission_status")).isEqualTo("MISSING");
                    assertThat(arc.get("expiry_date")).isNull();
                    assertThat(arc.get("task_id")).isNull();
                    assertThat(arc.get("file_id")).isNull();
                },
                passport -> {
                    assertThat(passport.get("document_type")).isEqualTo("PASSPORT_COPY");
                    assertThat(passport.get("submission_status")).isEqualTo("VERIFIED");
                    assertThat(passport.get("expiry_date")).isNotNull();
                    assertThat(passport.get("task_id")).isNull();
                    assertThat(passport.get("file_id")).isNull();
                }
        );
        assertThat(jdbcTemplate.queryForList(
                "SELECT worker_document_id FROM worker_document WHERE worker_document_id IN (?)",
                UUID.class,
                RETIRED_REPRESENTATIVE_DOCUMENT_IDS.toArray()
        )).isEmpty();
        assertThat(countWhere(
                "audit_event",
                "trace_id = ?",
                COMPANY_ID,
                "demo-compound-draft-flow"
        )).isZero();

        for (String table : List.of(
                "ai_run",
                "ai_attempt",
                "ai_question",
                "ai_candidate",
                "ai_candidate_decision_batch",
                "ai_candidate_decision",
                "ai_candidate_decision_task"
        )) {
            assertThat(count(table, COMPANY_ID))
                    .as("%s must not be pre-seeded for the Golden Flow", table)
                    .isZero();
        }
        assertThat(count("worker_link", COMPANY_ID)).isZero();
        assertThat(count("worker_response", COMPANY_ID)).isZero();
        assertThat(countWhere(
                "external_submission",
                "task_id IN (?, ?, ?)",
                COMPANY_ID,
                RECONTRACT_CANDIDATE_TASK_ID,
                EMPLOYMENT_EXTENSION_CANDIDATE_TASK_ID,
                PASSPORT_REQUEST_TASK_ID
        )).isZero();
        assertThat(countWhere(
                "task_evidence",
                "task_id IN (?, ?, ?)",
                COMPANY_ID,
                RECONTRACT_CANDIDATE_TASK_ID,
                EMPLOYMENT_EXTENSION_CANDIDATE_TASK_ID,
                PASSPORT_REQUEST_TASK_ID
        )).isZero();
    }

    private void assertGoldenFlowDocumentContext() {
        var resolution = aiSlotResolutionTransaction.resolve(
                COMPANY_ID,
                "0.3.0",
                new AiContextRequirement(
                        "EXPIRY_RENEWAL",
                        BigDecimal.ONE,
                        "응웬반A",
                        Map.of(),
                        List.of(
                                "passport_status",
                                "arc_status"
                        ),
                        "WF-STY-001",
                        "체류연장 준비",
                        AiConfidenceSource.MODEL,
                        null
                )
        );

        assertThat(resolution.resolvedFields())
                .containsEntry("passport_status", "VERIFIED")
                .containsEntry("arc_status", "MISSING");
        assertThat(resolution.missingFieldKeys()).isEmpty();
    }

    private void assertShowcaseApprovalAndSubmissionFixtures() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_request WHERE task_id = ? "
                        + "AND company_id = ? AND status = 'APPROVED'",
                Integer.class,
                COMPLETED_STAY_TASK_ID,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT safe_reference FROM external_submission WHERE task_id = ? AND company_id = ?",
                String.class,
                COMPLETED_STAY_TASK_ID,
                COMPANY_ID
        )).isEqualTo("DEMO-STAY-EXT-001");
        assertThat(jdbcTemplate.queryForList(
                "SELECT evidence_type FROM task_evidence WHERE task_id = ? AND company_id = ? "
                        + "ORDER BY evidence_type",
                String.class,
                COMPLETED_STAY_TASK_ID,
                COMPANY_ID
        )).containsExactly("OFFICIAL_RESULT", "RECEIPT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_evidence WHERE company_id = ? AND file_reference IS NOT NULL",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT file_reference FROM task_evidence WHERE task_id = ? "
                        + "AND company_id = ? AND file_reference IS NOT NULL ORDER BY evidence_type",
                String.class,
                COMPLETED_STAY_TASK_ID,
                COMPANY_ID
        )).containsExactly(STAY_RESULT_FILE_ID.toString(), STAY_RECEIPT_FILE_ID.toString());

    }

    private void assertStoredFileFixtures() throws Exception {
        List<Map<String, Object>> files = jdbcTemplate.queryForList(
                "SELECT stored_file_id, name, mime_type, size, purpose, task_id, worker_id, "
                        + "storage_key, scan_status, verified FROM stored_file "
                        + "WHERE company_id = ? ORDER BY stored_file_id",
                COMPANY_ID
        );
        assertThat(files).hasSize(3)
                .allMatch(file -> "application/pdf".equals(file.get("mime_type")))
                .allMatch(file -> "NOT_SCANNED".equals(file.get("scan_status")))
                .allMatch(file -> Boolean.FALSE.equals(file.get("verified")));

        Map<UUID, Map<String, Object>> filesById = files.stream().collect(Collectors.toMap(
                file -> (UUID) file.get("stored_file_id"),
                file -> file
        ));
        assertThat(filesById.get(CONTRACT_FILE_ID))
                .containsEntry("name", "demo-contract-renewal.pdf")
                .containsEntry("purpose", "DEMO_CONTRACT_RENEWAL")
                .containsEntry("task_id", CONTRACT_TASK_ID)
                .containsEntry("worker_id", CONTRACT_WORKER_ID)
                .containsEntry("storage_key", CONTRACT_FILE_ID.toString());
        assertThat(filesById.get(STAY_RECEIPT_FILE_ID))
                .containsEntry("name", "demo-stay-extension-receipt.pdf")
                .containsEntry("purpose", "DEMO_STAY_EXTENSION_RECEIPT")
                .containsEntry("task_id", COMPLETED_STAY_TASK_ID)
                .containsEntry("worker_id", COMPLETED_STAY_WORKER_ID)
                .containsEntry("storage_key", STAY_RECEIPT_FILE_ID.toString());
        assertThat(filesById.get(STAY_RESULT_FILE_ID))
                .containsEntry("name", "demo-stay-extension-result.pdf")
                .containsEntry("purpose", "DEMO_STAY_EXTENSION_RESULT")
                .containsEntry("task_id", COMPLETED_STAY_TASK_ID)
                .containsEntry("worker_id", COMPLETED_STAY_WORKER_ID)
                .containsEntry("storage_key", STAY_RESULT_FILE_ID.toString());

        Map<UUID, String> resources = Map.of(
                CONTRACT_FILE_ID, "demo/files/demo-contract-renewal.pdf",
                STAY_RECEIPT_FILE_ID, "demo/files/demo-stay-extension-receipt.pdf",
                STAY_RESULT_FILE_ID, "demo/files/demo-stay-extension-result.pdf"
        );
        for (Map<String, Object> file : files) {
            UUID fileId = (UUID) file.get("stored_file_id");
            byte[] expected;
            try (var input = new ClassPathResource(resources.get(fileId)).getInputStream()) {
                expected = input.readAllBytes();
            }
            Path actualPath = DEMO_FILE_STORAGE_PATH.resolve((String) file.get("storage_key"));
            assertThat(Files.readAllBytes(actualPath)).isEqualTo(expected);
            assertThat(((Number) file.get("size")).longValue()).isEqualTo(expected.length);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT file_id FROM worker_document WHERE worker_document_id = ? AND company_id = ?",
                UUID.class,
                UUID.fromString("95000000-0000-0000-0000-000000000007"),
                COMPANY_ID
        )).isEqualTo(CONTRACT_FILE_ID);
    }

    private void assertRepairsPartialStoredFileState() throws Exception {
        DefaultApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);

        Files.delete(DEMO_FILE_STORAGE_PATH.resolve(STAY_RESULT_FILE_ID.toString()));
        demoOperationalSeedRunner.run(arguments);
        assertThat(Files.isRegularFile(
                DEMO_FILE_STORAGE_PATH.resolve(STAY_RESULT_FILE_ID.toString())
        )).isTrue();

        jdbcTemplate.update(
                "DELETE FROM stored_file WHERE stored_file_id = ? AND company_id = ?",
                STAY_RECEIPT_FILE_ID,
                COMPANY_ID
        );
        demoOperationalSeedRunner.run(arguments);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE stored_file_id = ? AND company_id = ?",
                Integer.class,
                STAY_RECEIPT_FILE_ID,
                COMPANY_ID
        )).isEqualTo(1);

        assertStoredFileFixtures();
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

        assertThat(tasks).hasSize(21);
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
                "SELECT worker_id, company_id, display_name, nationality_code, preferred_language, "
                        + "work_status, stay_expiry_date, contract_start_date, contract_end_date, "
                        + "created_at, updated_at, version FROM worker "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, worker_id"
        ));
        snapshot.put("worker_document", snapshotRows(
                "SELECT worker_document_id, company_id, task_id, expiry_date, file_id, "
                        + "created_at, updated_at, version "
                        + "FROM worker_document WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, worker_document_id"
        ));
        snapshot.put("stored_file", snapshotRows(
                "SELECT stored_file_id, company_id, name, mime_type, size, purpose, task_id, "
                        + "worker_id, storage_key, scan_status, verified, created_at "
                        + "FROM stored_file WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, stored_file_id"
        ));
        snapshot.put("task", snapshotRows(
                "SELECT task_id, company_id, due_date, business_data_json, critical_fingerprint, "
                        + "content_revision, created_at, updated_at, version FROM task "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, task_id"
        ));
        snapshot.put("workflow_case", snapshotRows(
                "SELECT case_id, company_id, worker_id, title, lifecycle_status, priority, "
                        + "workflow_catalog_version, workflow_snapshot_json, created_by, "
                        + "created_at, updated_at, version FROM workflow_case "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, case_id"
        ));
        snapshot.put("task_checklist_item", snapshotRows(
                "SELECT checklist_item_id, company_id, completed_at, created_at, updated_at, version "
                        + "FROM task_checklist_item WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, checklist_item_id"
        ));
        snapshot.put("approval_request", snapshotRows(
                "SELECT approval_request_id, company_id, target_task_version, target_content_revision, "
                        + "approved_task_version, target_fingerprint, status, ai_snapshot_json, "
                        + "hr_snapshot_json, changed_fields_json, source_versions_json, requested_at, "
                        + "decided_at, decision_reason, invalidated_at, invalidation_reason, "
                        + "created_at, updated_at, version FROM approval_request "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, approval_request_id"
        ));
        snapshot.put("task_transition_history", snapshotRows(
                "SELECT transition_id, company_id, created_at FROM task_transition_history "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, transition_id"
        ));
        snapshot.put("external_submission", snapshotRows(
                "SELECT external_submission_id, company_id, destination, safe_reference, "
                        + "submitted_at, created_at "
                        + "FROM external_submission WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, external_submission_id"
        ));
        snapshot.put("task_evidence", snapshotRows(
                "SELECT evidence_id, company_id, evidence_type, file_reference, note, "
                        + "recorded_at, created_at FROM task_evidence "
                        + "WHERE company_id IN (?, ?) ORDER BY company_id, evidence_id"
        ));
        snapshot.put("document_request_draft", snapshotRows(
                "SELECT draft_id, company_id, language, message, review_status, "
                        + "created_at, updated_at, version "
                        + "FROM document_request_draft WHERE company_id IN (?, ?) "
                        + "ORDER BY company_id, draft_id"
        ));
        snapshot.put("document_request_draft_type", snapshotRows(
                "SELECT draft_type.draft_id, draft.company_id, draft_type.document_type "
                        + "FROM document_request_draft_type draft_type "
                        + "JOIN document_request_draft draft ON draft.draft_id = draft_type.draft_id "
                        + "WHERE draft.company_id IN (?, ?) "
                        + "ORDER BY draft.company_id, draft_type.draft_id, draft_type.document_type"
        ));
        snapshot.put("audit_event", snapshotRows(
                "SELECT audit_event_id, company_id, actor_type, action, target_type, target_id, "
                        + "trace_id, change_summary, created_at FROM audit_event "
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
        assertThat(JsonPath.<Number>read(tasks.body(), "$.total_elements").intValue()).isEqualTo(21);
        List<Map<String, Object>> taskItems = JsonPath.read(tasks.body(), "$.items");
        assertThat(taskItems).hasSize(21);
        HttpResponse<String> compoundCase = authorizedGet(
                "/api/v1/cases/" + COMPOUND_CASE_ID + "/projection",
                demoToken
        );
        assertThat(compoundCase.statusCode()).isEqualTo(404);
        assertStatusApiCount(demoToken, "READY_FOR_REVIEW", 3);
        assertStatusApiCount(demoToken, "DRAFT", 2);
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
        assertThat(JsonPath.<Number>read(documents.body(), "$.total_elements").intValue()).isEqualTo(83);
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
        assertThat(JsonPath.<List<?>>read(demoAudits.body(), "$.items")).hasSize(94);
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
