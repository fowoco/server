package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.renewal.RenewalWorkflowPolicy;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.workerlink.application.WorkerResponsePayloadCodec;
import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenewalContinuationService {

    private static final String FALLBACK_INSTRUCTION =
            "재계약·취업활동기간·체류기간 연장 업무를 최신 정보로 다시 분석해줘";

    private final TenantDatabaseContext tenantContext;
    private final TaskRepository taskRepository;
    private final WorkerResponseRepository workerResponseRepository;
    private final WorkerResponsePayloadCodec responsePayloadCodec;
    private final TaskContentCodec taskContentCodec;
    private final RenewalInstructionLookup instructionLookup;
    private final RenewalExecutionService executionService;

    public RenewalContinuationService(
            TenantDatabaseContext tenantContext,
            TaskRepository taskRepository,
            WorkerResponseRepository workerResponseRepository,
            WorkerResponsePayloadCodec responsePayloadCodec,
            TaskContentCodec taskContentCodec,
            RenewalInstructionLookup instructionLookup,
            RenewalExecutionService executionService
    ) {
        this.tenantContext = tenantContext;
        this.taskRepository = taskRepository;
        this.workerResponseRepository = workerResponseRepository;
        this.responsePayloadCodec = responsePayloadCodec;
        this.taskContentCodec = taskContentCodec;
        this.instructionLookup = instructionLookup;
        this.executionService = executionService;
    }

    @Transactional
    public void continueAfterWorkerAnswers(DomainEventEnvelope event) {
        tenantContext.setCompanyIdForCurrentTransaction(event.companyId());
        Task task = taskRepository.findByIdAndCompanyId(event.aggregateId(), event.companyId())
                .orElse(null);
        if (task == null || !supports(task)) {
            return;
        }
        UUID responseId = responseId(event);
        WorkerResponseRepository.WorkerResponseItem response = workerResponseRepository
                .findByResponseIdAndTaskIdAndCompanyId(responseId, task.taskId(), event.companyId())
                .orElse(null);
        if (response == null
                || response.response().responseType() != WorkerResponseType.SLOT_ANSWERS_SUBMITTED) {
            return;
        }
        Map<String, String> answers = responsePayloadCodec.decodeAnswers(
                response.response().answersJson()
        );
        if (answers.isEmpty()) {
            return;
        }
        executionService.executeWorkerContinuation(
                task.taskId(),
                instruction(task),
                task.version(),
                answers,
                delegatedActor(event),
                metadata(event),
                event.eventId()
        );
    }

    @Transactional
    public void continueAfterOcrApproval(DomainEventEnvelope event) {
        tenantContext.setCompanyIdForCurrentTransaction(event.companyId());
        Task sourceTask = taskRepository.findByIdAndCompanyId(event.aggregateId(), event.companyId())
                .orElse(null);
        if (sourceTask == null) {
            return;
        }
        Task target = resolveOcrTarget(sourceTask).orElse(null);
        if (target == null) {
            return;
        }
        executionService.executeOcrContinuation(
                target.taskId(),
                instruction(target),
                target.version(),
                delegatedActor(event),
                metadata(event),
                event.eventId()
        );
    }

    private Optional<Task> resolveOcrTarget(Task sourceTask) {
        if (supports(sourceTask) && requestsOcr(sourceTask)) {
            return Optional.of(sourceTask);
        }
        if (sourceTask.caseId() == null) {
            return Optional.empty();
        }
        List<Task> caseTasks = taskRepository.findAll(new TaskRepository.TaskSearchCriteria(
                sourceTask.companyId(), null, null, null, null, null, sourceTask.caseId(),
                null, null, null, 0, 100
        )).items();
        return caseTasks.stream()
                .filter(this::supports)
                .filter(this::requestsOcr)
                .min(Comparator
                        .comparingInt(this::candidateOrder)
                        .thenComparing(task -> task.taskId().toString()));
    }

    private boolean supports(Task task) {
        return !task.status().isTerminal()
                && RenewalWorkflowPolicy.supports(task.taskType().name(), task.workflowId());
    }

    private boolean requestsOcr(Task task) {
        Object execution = businessData(task).get("renewal_execution");
        if (!(execution instanceof Map<?, ?> executionMap)) {
            return false;
        }
        Object fields = executionMap.get("requested_fields");
        if (!(fields instanceof Iterable<?> iterable)) {
            return false;
        }
        for (Object field : iterable) {
            if (field instanceof Map<?, ?> requested
                    && "DOCUMENT_OCR".equals(requested.get("source_hint"))) {
                return true;
            }
        }
        return false;
    }

    private int candidateOrder(Task task) {
        Object value = businessData(task).get("candidate_order");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.MAX_VALUE;
    }

    private String instruction(Task task) {
        Object value = businessData(task).get("ai_run_id");
        if (value instanceof String aiRunId) {
            try {
                return instructionLookup.findInstruction(UUID.fromString(aiRunId), task.companyId())
                        .orElse(FALLBACK_INSTRUCTION);
            } catch (IllegalArgumentException ignored) {
                return FALLBACK_INSTRUCTION;
            }
        }
        return FALLBACK_INSTRUCTION;
    }

    private Map<String, Object> businessData(Task task) {
        return taskContentCodec.decodeBusinessData(task.businessDataJson());
    }

    private ActorContext delegatedActor(DomainEventEnvelope event) {
        return new ActorContext(event.actorId(), event.companyId(), Set.of(UserRole.HR));
    }

    private RequestMetadata metadata(DomainEventEnvelope event) {
        return new RequestMetadata("outbox:" + event.eventId(), event.traceId());
    }

    private UUID responseId(DomainEventEnvelope event) {
        try {
            return UUID.fromString(event.requestId());
        } catch (IllegalArgumentException exception) {
            return event.eventId();
        }
    }
}
