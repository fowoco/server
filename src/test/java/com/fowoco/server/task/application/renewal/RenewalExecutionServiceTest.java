package com.fowoco.server.task.application.renewal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.renewal.RenewalGeneratedDocument;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.aiintegration.application.renewal.RenewalTaskSnapshot;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.web.RequestMetadata;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenewalExecutionServiceTest {

    private static final UUID TASK_ID = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("82000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_ID = UUID.fromString("82000000-0000-0000-0000-000000000003");
    private static final UUID ACTOR_ID = UUID.fromString("82000000-0000-0000-0000-000000000004");
    private static final UUID EVENT_ID = UUID.fromString("82000000-0000-0000-0000-000000000005");

    private final RenewalExecutionContextReader contextReader = mock(RenewalExecutionContextReader.class);
    private final RenewalRuntimeClient runtimeClient = mock(RenewalRuntimeClient.class);
    private final RenewalExecutionResultApplier resultApplier = mock(RenewalExecutionResultApplier.class);
    private final GeneratedDocumentService generatedDocumentService = mock(GeneratedDocumentService.class);
    private final UuidGenerator uuidGenerator = mock(UuidGenerator.class);
    private final RenewalExecutionService service = new RenewalExecutionService(
            contextReader,
            runtimeClient,
            resultApplier,
            generatedDocumentService,
            new RenewalExecutionTelemetry(new SimpleMeterRegistry()),
            uuidGenerator
    );
    private final ActorContext actor = new ActorContext(ACTOR_ID, COMPANY_ID, Set.of(UserRole.HR));
    private final RequestMetadata metadata = new RequestMetadata("continuation-request", null);
    private RenewalExecutionContext context;
    private RenewalRunResponse response;
    private List<PreparedRenewalDocument> preparedDocuments;

    @BeforeEach
    void setUp() {
        context = new RenewalExecutionContext(
                TASK_ID, COMPANY_ID, WORKER_ID, Map.of(), Map.of(), List.of(), null,
                null, null, taskSnapshot()
        );
        RenewalGeneratedDocument document = new RenewalGeneratedDocument(
                "standard_labor_contract_v6", "표준근로계약서", "hwp", "READY",
                null, null, List.of(), List.of(), Map.of("worker_name", "응웬반안")
        );
        response = new RenewalRunResponse(
                EVENT_ID, UUID.randomUUID(), TASK_ID,
                "EXPIRY_RENEWAL", "WF-STY-001", new BigDecimal("0.95"),
                "READY_FOR_REVIEW", "ARTIFACT_READY", "generate", "ACT", "GENERATE",
                Map.of(), List.of(), List.of(), null, null, false, null, null, null,
                List.of(document), List.of(), null, List.of(), List.of(), null, null, null, List.of()
        );
        preparedDocuments = List.of(mock(PreparedRenewalDocument.class));
        when(runtimeClient.run(any(), any())).thenReturn(response);
        when(generatedDocumentService.prepare("RECONTRACT", response.generatedDocuments()))
                .thenReturn(preparedDocuments);
        when(resultApplier.apply(any(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(mock(RenewalExecutionResult.class));
    }

    @Test
    void workerAnswerContinuationGeneratesDraftWhenAgentReturnsGenerate() {
        Map<String, String> answers = Map.of("lodging", "기숙사 제공");
        when(contextReader.loadWorkerContinuation(TASK_ID, 3L, answers, actor))
                .thenReturn(context);

        service.executeWorkerContinuation(
                TASK_ID, "재계약 준비", 3L, answers, actor, metadata, EVENT_ID
        );

        verify(generatedDocumentService).prepare("RECONTRACT", response.generatedDocuments());
        verify(resultApplier).apply(
                eq(TASK_ID), eq(3L), eq(response), eq(preparedDocuments),
                eq(context.submittedSlotAnswers()), eq(actor), eq(metadata)
        );
    }

    @Test
    void ocrApprovalContinuationGeneratesDraftWhenAgentReturnsGenerate() {
        when(contextReader.load(TASK_ID, 4L, Map.of(), actor)).thenReturn(context);

        service.executeOcrContinuation(
                TASK_ID, "재계약 준비", 4L, actor, metadata, EVENT_ID
        );

        verify(generatedDocumentService).prepare("RECONTRACT", response.generatedDocuments());
        verify(resultApplier).apply(
                eq(TASK_ID), eq(4L), eq(response), eq(preparedDocuments),
                eq(context.submittedSlotAnswers()), eq(actor), eq(metadata)
        );
    }

    private RenewalTaskSnapshot taskSnapshot() {
        return new RenewalTaskSnapshot(
                TASK_ID, COMPANY_ID, WORKER_ID, null,
                "RECONTRACT", "WF-CON-001", "0.3.1",
                "재계약 조건 확인", null, Map.of(), 0,
                "MANUAL", "DRAFT", null, ACTOR_ID, ACTOR_ID,
                null, null, 0
        );
    }
}
