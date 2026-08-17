package com.fowoco.server.airun.application;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.REQUEST_ID;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validPlanRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validVersions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.AiConfidenceSource;
import com.fowoco.server.aiintegration.application.model.AiQuestion;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.fowoco.server.aiintegration.application.validation.AiRuntimeBoundaryPolicy;
import com.fowoco.server.aiintegration.application.validation.AiRuntimeContractValidator;
import com.fowoco.server.aiintegration.application.validation.ValidatingAiRuntimeClient;
import com.fowoco.server.airun.application.error.AiContextResolutionException;
import com.fowoco.server.airun.application.error.AiContextResolutionFailureCode;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import com.fowoco.server.workflow.application.WorkflowCatalogService;
import com.fowoco.server.workflow.domain.WorkflowCatalog;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiAnalysisContinuationServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID NEXT_ATTEMPT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void recordsNewAttemptBeforeAnalyzeAndPreservesPlanContext() {
        List<String> callOrder = new ArrayList<>();
        AtomicReference<AiAnalysisRequest> receivedRequest = new AtomicReference<>();
        AiRuntimeClient transport = (request, context) -> {
            callOrder.add("runtime");
            receivedRequest.set(request);
            return needsInfoResponse(request.requestId());
        };
        AiRuntimeClient validatingClient = new ValidatingAiRuntimeClient(
                transport,
                new AiRuntimeContractValidator(new AiRuntimeBoundaryPolicy())
        );
        AiAnalysisContinuationService service = new AiAnalysisContinuationService(
                resolutionTransaction(),
                (companyId, requestId, phase, contextRound, analysisInput) -> {
                    assertThat(companyId).isEqualTo(COMPANY_ID);
                    assertThat(requestId).isEqualTo(REQUEST_ID);
                    assertThat(phase).isEqualTo(AiAnalysisPhase.ANALYZE);
                    assertThat(contextRound).isEqualTo(1);
                    assertThat(analysisInput.requestedFieldKeys())
                            .containsExactly(
                                    "worker_id",
                                    "stay_expiry_date",
                                    "passport_status",
                                    "arc_status",
                                    "due_at"
                            );
                    callOrder.add("attempt");
                    return NEXT_ATTEMPT_ID;
                },
                validatingClient,
                new AiRunExecutionTelemetry(new SimpleMeterRegistry())
        );

        AiAnalysisContinuationResult result = service.continueAnalysis(
                COMPANY_ID,
                validPlanRequest(),
                contextRequiredResponse(),
                0,
                9_000,
                com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext.withoutTrace()
        );

        assertThat(callOrder).containsExactly("attempt", "runtime");
        assertThat(result.attemptId()).isEqualTo(NEXT_ATTEMPT_ID);
        assertThat(result.response().outcome()).isEqualTo(AiAnalysisOutcome.NEEDS_INFO);
        assertThat(result.missingFieldKeys()).containsExactly("due_at");

        AiAnalysisRequest analyzeRequest = receivedRequest.get();
        assertThat(analyzeRequest.requestId()).isEqualTo(REQUEST_ID);
        assertThat(analyzeRequest.attemptId()).isEqualTo(NEXT_ATTEMPT_ID);
        assertThat(analyzeRequest.phase()).isEqualTo(AiAnalysisPhase.ANALYZE);
        assertThat(analyzeRequest.analysisInput().instruction())
                .isEqualTo(validPlanRequest().analysisInput().instruction());
        assertThat(analyzeRequest.analysisInput().instruction())
                .isEqualTo("응웬반안 체류연장 준비해줘");
        assertThat(analyzeRequest.analysisInput().plannedIntentDecision().detectedIntent())
                .isEqualTo("EXPIRY_RENEWAL");
        assertThat(analyzeRequest.analysisInput().plannedIntentDecision().workflowId())
                .isEqualTo("WF-STY-001");
        assertThat(analyzeRequest.analysisInput().plannedIntentDecision().evidence())
                .isEqualTo("체류연장 준비해줘");
        assertThat(analyzeRequest.analysisInput().extractedSlots())
                .containsEntry("document_type", "STAY_EXTENSION");
        assertThat(analyzeRequest.analysisInput().requestedFieldKeys())
                .containsExactly(
                        "worker_id",
                        "stay_expiry_date",
                        "passport_status",
                        "arc_status",
                        "due_at"
                );
        assertThat(analyzeRequest.analysisInput().workers()).hasSize(1);
        assertThat(analyzeRequest.analysisInput().workers().get(0).requestedFields())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "worker_id", WORKER_ID.toString(),
                        "stay_expiry_date", "2026-09-30",
                        "passport_status", "MISSING",
                        "arc_status", "MISSING"
                ));
    }

    @Test
    void stopsAtRoundLimitBeforeDatabaseResolutionOrRuntimeCall() {
        AtomicReference<Boolean> attemptStarted = new AtomicReference<>(false);
        AiAnalysisContinuationService service = new AiAnalysisContinuationService(
                resolutionTransaction(),
                (companyId, requestId, phase, contextRound, analysisInput) -> {
                    attemptStarted.set(true);
                    return NEXT_ATTEMPT_ID;
                },
                (request, context) -> {
                    throw new AssertionError("Runtime must not be called after the round limit.");
                },
                new AiRunExecutionTelemetry(new SimpleMeterRegistry())
        );

        assertThatThrownBy(() -> service.continueAnalysis(
                COMPANY_ID,
                validPlanRequest(),
                contextRequiredResponse(),
                AiAnalysisContinuationService.MAX_CONTEXT_ROUNDS,
                9_000,
                com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext.withoutTrace()
        )).isInstanceOfSatisfying(AiContextResolutionException.class, exception ->
                assertThat(exception.failureCode()).isEqualTo(
                        AiContextResolutionFailureCode.CONTEXT_ROUND_LIMIT
                )
        );
        assertThat(attemptStarted.get()).isFalse();
    }

    private AiSlotResolutionTransaction resolutionTransaction() {
        WorkflowCatalogService workflowService = new WorkflowCatalogService(this::catalog);
        return new AiSlotResolutionTransaction(
                workflowService,
                (companyId, displayName) -> List.of(worker()),
                companyId -> {
                }
        );
    }

    private AiAnalysisResponse contextRequiredResponse() {
        return new AiAnalysisResponse(
                REQUEST_ID,
                AiAnalysisOutcome.CONTEXT_REQUIRED,
                new AiContextRequirement(
                        "EXPIRY_RENEWAL",
                        new BigDecimal("0.94"),
                        "응웬반안",
                        Map.of("document_type", "STAY_EXTENSION"),
                        List.of(
                                "worker_id",
                                "stay_expiry_date",
                                "passport_status",
                                "arc_status",
                                "due_at"
                        ),
                        "WF-STY-001",
                        "체류연장 준비해줘",
                        AiConfidenceSource.MODEL,
                        null
                ),
                List.of(),
                List.of(),
                List.of(),
                validVersions(),
                1,
                120
        );
    }

    private AiAnalysisResponse needsInfoResponse(UUID requestId) {
        return new AiAnalysisResponse(
                requestId,
                AiAnalysisOutcome.NEEDS_INFO,
                null,
                List.of(new AiQuestion("due_at", "내부 준비 마감일을 입력해 주세요.")),
                List.of(),
                List.of(),
                validVersions(),
                1,
                180
        );
    }

    private WorkflowCatalog catalog() {
        Set<String> allowedSlots = Set.of(
                "worker_id",
                "due_at",
                "stay_expiry_date",
                "passport_status",
                "arc_status"
        );
        WorkflowDefinition workflow = new WorkflowDefinition(
                "WF-STY-001",
                "체류기간 연장",
                "EXPIRY_RENEWAL",
                "high",
                Set.of(TaskType.STAY_PERIOD_EXTENSION),
                Set.of("worker_id", "due_at"),
                allowedSlots,
                allowedSlots,
                List.of(),
                List.of(),
                List.of()
        );
        return new WorkflowCatalog(
                "FOWOCO-KNOWLEDGE",
                "0.3.1",
                "DRAFT",
                "fowoco/knowledge",
                Instant.parse("2026-07-23T00:00:00Z"),
                List.of(workflow)
        );
    }

    private WorkerAiContextSnapshot worker() {
        return new WorkerAiContextSnapshot(
                WORKER_ID,
                COMPANY_ID,
                "응웬반안",
                "VN",
                "vi",
                "ACTIVE",
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 31)
        );
    }
}
