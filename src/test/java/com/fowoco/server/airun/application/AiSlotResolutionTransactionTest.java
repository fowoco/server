package com.fowoco.server.airun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
import com.fowoco.server.airun.application.error.AiContextResolutionException;
import com.fowoco.server.airun.application.error.AiContextResolutionFailureCode;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import com.fowoco.server.worker.application.port.WorkerAiContextReader;
import com.fowoco.server.workflow.application.WorkflowCatalogService;
import com.fowoco.server.workflow.domain.WorkflowCatalog;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiSlotResolutionTransactionTest {

    private static final UUID COMPANY_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void resolvesOnlyAllowListedWorkerFieldsAndReportsMissingValues() {
        AtomicReference<UUID> boundCompany = new AtomicReference<>();
        WorkerAiContextReader reader = (companyId, displayName) -> List.of(worker(COMPANY_A));
        AiSlotResolutionTransaction transaction = transaction(reader, boundCompany);

        AiSlotResolution result = transaction.resolve(
                COMPANY_A,
                "0.3.1",
                requirement(List.of(
                        "worker_id",
                        "stay_expiry_date",
                        "passport_status",
                        "arc_status",
                        "due_at"
                ))
        );

        assertThat(boundCompany.get()).isEqualTo(COMPANY_A);
        assertThat(result.worker().workerRef()).isEqualTo(WORKER_A);
        assertThat(result.resolvedFields()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "worker_id", WORKER_A.toString(),
                "stay_expiry_date", "2026-09-30",
                "passport_status", "MISSING",
                "arc_status", "MISSING"
        ));
        assertThat(result.missingFieldKeys()).containsExactly("due_at");
        assertThat(result.workflowConstraints())
                .extracting(constraint -> constraint.workflowId())
                .containsExactly("WF-CON-001", "WF-STY-001");
    }

    @Test
    void rejectsFieldOutsideKnowledgeAllowListBeforeReadingWorkerData() {
        AtomicReference<Boolean> readerCalled = new AtomicReference<>(false);
        WorkerAiContextReader reader = (companyId, displayName) -> {
            readerCalled.set(true);
            return List.of(worker(COMPANY_A));
        };

        assertFailure(
                () -> transaction(reader, new AtomicReference<>()).resolve(
                        COMPANY_A,
                        "0.3.1",
                        requirement(List.of("legal_name"))
                ),
                AiContextResolutionFailureCode.FORBIDDEN_FIELD
        );
        assertThat(readerCalled.get()).isFalse();
    }

    @Test
    void rejectsAnalysisPinnedToADifferentKnowledgeVersion() {
        assertFailure(
                () -> transaction(
                        (companyId, displayName) -> List.of(worker(COMPANY_A)),
                        new AtomicReference<>()
                ).resolve(COMPANY_A, "9.9.9", requirement(List.of("worker_id"))),
                AiContextResolutionFailureCode.KNOWLEDGE_VERSION_MISMATCH
        );
    }

    @Test
    void rejectsWorkflowThatDoesNotBelongToTheDetectedIntent() {
        AiContextRequirement valid = requirement(List.of("worker_id"));
        AiContextRequirement mismatched = new AiContextRequirement(
                valid.detectedIntent(),
                valid.confidence(),
                valid.targetDisplayName(),
                valid.extractedSlots(),
                valid.requiredFieldKeys(),
                "WF-PAY-001",
                valid.evidence(),
                valid.confidenceSource(),
                valid.bertRoutingScore()
        );

        assertFailure(
                () -> transaction(
                        (companyId, displayName) -> List.of(worker(COMPANY_A)),
                        new AtomicReference<>()
                ).resolve(COMPANY_A, "0.3.1", mismatched),
                AiContextResolutionFailureCode.UNSUPPORTED_WORKFLOW
        );
    }

    @Test
    void distinguishesMissingAmbiguousAndCrossCompanyTargetsWithoutLeakingNames() {
        assertFailure(
                () -> transaction((companyId, displayName) -> List.of(), new AtomicReference<>())
                        .resolve(COMPANY_A, "0.3.1", requirement(List.of("worker_id"))),
                AiContextResolutionFailureCode.TARGET_NOT_FOUND
        );

        assertFailure(
                () -> transaction(
                        (companyId, displayName) -> List.of(worker(COMPANY_A), worker(COMPANY_A)),
                        new AtomicReference<>()
                ).resolve(COMPANY_A, "0.3.1", requirement(List.of("worker_id"))),
                AiContextResolutionFailureCode.TARGET_AMBIGUOUS
        );

        assertThatThrownBy(() -> transaction(
                (companyId, displayName) -> List.of(worker(COMPANY_B)),
                new AtomicReference<>()
        ).resolve(COMPANY_A, "0.3.1", requirement(List.of("worker_id"))))
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception -> {
                    assertThat(exception.failureCode()).isEqualTo(
                            AiContextResolutionFailureCode.TARGET_NOT_FOUND
                    );
                    assertThat(exception.getMessage()).doesNotContain("응웬반안");
                });
    }

    private AiSlotResolutionTransaction transaction(
            WorkerAiContextReader reader,
            AtomicReference<UUID> boundCompany
    ) {
        WorkflowCatalogService workflowService = new WorkflowCatalogService(this::catalog);
        return new AiSlotResolutionTransaction(
                workflowService,
                reader,
                boundCompany::set
        );
    }

    private WorkflowCatalog catalog() {
        return new WorkflowCatalog(
                "FOWOCO-KNOWLEDGE",
                "0.3.1",
                "DRAFT",
                "fowoco/knowledge",
                Instant.parse("2026-07-23T00:00:00Z"),
                List.of(
                        workflow(
                                "WF-STY-001",
                                Set.of(
                                        "worker_id",
                                        "due_at",
                                        "stay_expiry_date",
                                        "passport_status",
                                        "arc_status"
                                )
                        ),
                        workflow(
                                "WF-CON-001",
                                Set.of("worker_id", "due_at", "contract_end_date")
                        )
                )
        );
    }

    private WorkflowDefinition workflow(String workflowId, Set<String> slots) {
        return new WorkflowDefinition(
                workflowId,
                workflowId,
                "EXPIRY_RENEWAL",
                "high",
                Set.of(TaskType.STAY_PERIOD_EXTENSION),
                Set.of("worker_id", "due_at"),
                slots,
                slots,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private AiContextRequirement requirement(List<String> requiredFieldKeys) {
        return new AiContextRequirement(
                "EXPIRY_RENEWAL",
                new BigDecimal("0.94"),
                "응웬반안",
                Map.of("document_type", "STAY_EXTENSION"),
                requiredFieldKeys,
                "WF-STY-001",
                "체류연장 준비",
                AiConfidenceSource.MODEL,
                null
        );
    }

    private WorkerAiContextSnapshot worker(UUID companyId) {
        return new WorkerAiContextSnapshot(
                WORKER_A,
                companyId,
                "응웬반안",
                "VN",
                "vi",
                "ACTIVE",
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 31)
        );
    }

    private void assertFailure(Runnable invocation, AiContextResolutionFailureCode expectedCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                        assertThat(exception.failureCode()).isEqualTo(expectedCode)
                );
    }
}
