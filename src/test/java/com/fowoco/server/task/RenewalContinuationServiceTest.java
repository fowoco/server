package com.fowoco.server.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.application.renewal.RenewalContinuationService;
import com.fowoco.server.task.application.renewal.RenewalExecutionService;
import com.fowoco.server.task.application.renewal.RenewalInstructionLookup;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.workerlink.application.WorkerResponsePayloadCodec;
import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.domain.ConversationStatus;
import com.fowoco.server.workerlink.domain.WorkerResponse;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenewalContinuationServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("71000000-0000-0000-0000-000000000002");
    private static final UUID WORKER_ID = UUID.fromString("71000000-0000-0000-0000-000000000003");
    private static final UUID CASE_ID = UUID.fromString("71000000-0000-0000-0000-000000000004");

    private final TenantDatabaseContext tenantContext = mock(TenantDatabaseContext.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final WorkerResponseRepository responseRepository = mock(WorkerResponseRepository.class);
    private final WorkerResponsePayloadCodec responseCodec = mock(WorkerResponsePayloadCodec.class);
    private final TaskContentCodec taskContentCodec = mock(TaskContentCodec.class);
    private final RenewalInstructionLookup instructionLookup = mock(RenewalInstructionLookup.class);
    private final RenewalExecutionService executionService = mock(RenewalExecutionService.class);
    private RenewalContinuationService service;

    @BeforeEach
    void setUp() {
        service = new RenewalContinuationService(
                tenantContext,
                taskRepository,
                responseRepository,
                responseCodec,
                taskContentCodec,
                instructionLookup,
                executionService
        );
    }

    @Test
    void workerAnswersResumeTheSameRenewalTaskOnce() {
        Task task = task(
                UUID.fromString("72000000-0000-0000-0000-000000000001"),
                TaskType.RECONTRACT,
                "WF-CON-001",
                "worker-task",
                TaskStatus.APPROVED
        );
        UUID responseId = UUID.fromString("72000000-0000-0000-0000-000000000002");
        UUID workerLinkId = UUID.fromString("72000000-0000-0000-0000-000000000003");
        WorkerResponse response = WorkerResponse.create(
                responseId,
                workerLinkId,
                COMPANY_ID,
                WorkerResponseType.SLOT_ANSWERS_SUBMITTED,
                null,
                "{\"lodging\":\"기숙사 제공\"}",
                "worker-answer-key",
                "a".repeat(64),
                Instant.parse("2026-08-13T00:00:00Z")
        );
        when(taskRepository.findByIdAndCompanyId(task.taskId(), COMPANY_ID))
                .thenReturn(Optional.of(task));
        when(responseRepository.findByResponseIdAndTaskIdAndCompanyId(
                responseId, task.taskId(), COMPANY_ID
        )).thenReturn(Optional.of(new WorkerResponseRepository.WorkerResponseItem(
                response, ConversationStatus.NEEDS_FOLLOWUP, List.of()
        )));
        when(responseCodec.decodeAnswers(response.answersJson()))
                .thenReturn(Map.of("lodging", "기숙사 제공"));
        when(taskContentCodec.decodeBusinessData("worker-task"))
                .thenReturn(Map.of(
                        "renewal_execution", Map.of(
                                "requested_fields", List.of(Map.of(
                                        "key", "lodging", "source_hint", "USER_INPUT"
                                ))
                        )
                ));

        DomainEventEnvelope event = event(
                UUID.fromString("72000000-0000-0000-0000-000000000004"),
                "WorkerSlotAnswersSubmitted",
                task.taskId(),
                responseId.toString()
        );
        service.continueAfterWorkerAnswers(event);

        verify(executionService).executeWorkerContinuation(
                eq(task.taskId()),
                any(String.class),
                eq(task.version()),
                eq(Map.of("lodging", "기숙사 제공")),
                any(),
                any(),
                eq(event.eventId())
        );
    }

    @Test
    void ocrApprovalSelectsTheFirstCaseTaskThatRequestedOcr() {
        Task source = task(
                UUID.fromString("73000000-0000-0000-0000-000000000001"),
                TaskType.DOCUMENT_REQUEST,
                "WF-DOC-001",
                "source-task",
                TaskStatus.WAITING_WORKER
        );
        Task first = task(
                UUID.fromString("73000000-0000-0000-0000-000000000002"),
                TaskType.RECONTRACT,
                "WF-CON-001",
                "first-task",
                TaskStatus.NEEDS_INFO
        );
        Task second = task(
                UUID.fromString("73000000-0000-0000-0000-000000000003"),
                TaskType.STAY_PERIOD_EXTENSION,
                "WF-STY-001",
                "second-task",
                TaskStatus.NEEDS_INFO
        );
        when(taskRepository.findByIdAndCompanyId(source.taskId(), COMPANY_ID))
                .thenReturn(Optional.of(source));
        when(taskRepository.findAll(any())).thenReturn(new TaskRepository.TaskPage(
                List.of(second, first), 0, 100, 2, 1
        ));
        when(taskContentCodec.decodeBusinessData("source-task")).thenReturn(Map.of());
        when(taskContentCodec.decodeBusinessData("first-task")).thenReturn(renewalData(1));
        when(taskContentCodec.decodeBusinessData("second-task")).thenReturn(renewalData(2));

        DomainEventEnvelope event = event(
                UUID.fromString("73000000-0000-0000-0000-000000000004"),
                "DocumentOcrApproved",
                source.taskId(),
                "ocr-review-request"
        );
        service.continueAfterOcrApproval(event);

        verify(executionService).executeOcrContinuation(
                eq(first.taskId()),
                any(String.class),
                eq(first.version()),
                any(),
                any(),
                eq(event.eventId())
        );
    }

    private Map<String, Object> renewalData(int order) {
        return Map.of(
                "candidate_order", order,
                "renewal_execution", Map.of(
                        "requested_fields", List.of(Map.of(
                                "key", "passport_number", "source_hint", "DOCUMENT_OCR"
                        ))
                )
        );
    }

    private Task task(
            UUID taskId,
            TaskType taskType,
            String workflowId,
            String businessDataJson,
            TaskStatus status
    ) {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        return new Task(
                taskId,
                COMPANY_ID,
                WORKER_ID,
                CASE_ID,
                taskType,
                workflowId,
                "0.2.0",
                "테스트 업무",
                null,
                businessDataJson,
                "0".repeat(64),
                0,
                TaskSource.AI_CANDIDATE,
                status,
                LocalDate.of(2026, 8, 20),
                ACTOR_ID,
                ACTOR_ID,
                now,
                now,
                0
        );
    }

    private DomainEventEnvelope event(
            UUID eventId,
            String eventType,
            UUID taskId,
            String requestId
    ) {
        return new DomainEventEnvelope(
                eventId,
                eventType,
                "1",
                "Task",
                taskId,
                COMPANY_ID,
                EventActorType.HR_USER,
                ACTOR_ID,
                requestId,
                null,
                Instant.parse("2026-08-13T00:00:00Z"),
                SafeEventPayload.empty()
        );
    }
}
