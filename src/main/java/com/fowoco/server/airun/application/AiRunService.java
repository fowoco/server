package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.aiintegration.application.model.WorkerContext;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.fowoco.server.aiintegration.application.port.AiRuntimeDeadlinePolicy;
import com.fowoco.server.airun.application.error.AiContextResolutionException;
import com.fowoco.server.airun.application.error.AiRunErrorCode;
import com.fowoco.server.airun.application.port.AiAttemptStarter;
import com.fowoco.server.airun.application.port.AiRunRepository;
import com.fowoco.server.airun.application.port.AiRunPublicEventPublisher;
import com.fowoco.server.airun.application.port.AiRunRepository.ExecutionState;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workflow.application.WorkflowCatalogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the demo vertical slice: persist first, call Runtime without a database transaction,
 * and persist the validated result in a new transaction.
 */
@Service
public class AiRunService implements AiAttemptStarter {

    private static final String CONTRACT_VERSION = "1.1.0";
    private static final int MAX_INSTRUCTION_LENGTH = 10_000;
    private static final Pattern SLOT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");
    private static final String AUDIT_EVENT_VERSION = "1";

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final AiRunRepository repository;
    private final AiRuntimeClient runtimeClient;
    private final AiRuntimeDeadlinePolicy runtimeDeadlinePolicy;
    private final AiSlotResolutionTransaction slotResolutionTransaction;
    private final WorkflowCatalogService workflowCatalogService;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final AuditEventRepository auditEventRepository;
    private final AiRunPublicEventPublisher publicEventPublisher;
    private final Executor aiRunTaskExecutor;

    public AiRunService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            AiRunRepository repository,
            AiRuntimeClient runtimeClient,
            AiRuntimeDeadlinePolicy runtimeDeadlinePolicy,
            AiSlotResolutionTransaction slotResolutionTransaction,
            WorkflowCatalogService workflowCatalogService,
            UuidGenerator uuidGenerator,
            Clock clock,
            TransactionTemplate transactionTemplate,
            AuditEventRepository auditEventRepository,
            AiRunPublicEventPublisher publicEventPublisher,
            @Qualifier("aiRunTaskExecutor") Executor aiRunTaskExecutor
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.repository = repository;
        this.runtimeClient = runtimeClient;
        this.runtimeDeadlinePolicy = runtimeDeadlinePolicy;
        this.slotResolutionTransaction = slotResolutionTransaction;
        this.workflowCatalogService = workflowCatalogService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.auditEventRepository = auditEventRepository;
        this.publicEventPublisher = publicEventPublisher;
        this.aiRunTaskExecutor = aiRunTaskExecutor;
    }

    public AiRunResult createAndSchedule(
            String instruction,
            String idempotencyKey,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        String normalizedInstruction = normalizeInstruction(instruction);
        String instructionHash = sha256(normalizedInstruction);
        String keyHash = sha256(normalizeIdempotencyKey(idempotencyKey));

        AiRunCreation creation = createPlan(
                normalizedInstruction,
                instructionHash,
                keyHash,
                actor,
                metadata
        );
        publishCurrent(creation.aiRunId(), creation.companyId());
        AiRunResult accepted = requireRun(creation.aiRunId(), actor);
        if (creation.newlyCreated()) {
            schedulePlan(creation);
        }
        return accepted;
    }

    public AiRunResult requireRun(UUID aiRunId, ActorContext actor) {
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        return inTenant(actor.companyId(), () -> repository
                .findByIdAndCompanyId(aiRunId, actor.companyId())
                .orElseThrow(() -> new ApiException(AiRunErrorCode.AI_RUN_NOT_FOUND)));
    }

    public AiRunResult answerAndExecute(
            UUID aiRunId,
            long expectedVersion,
            Map<String, String> answers,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        Map<String, String> normalizedAnswers = normalizeAnswers(answers);
        ExecutionState current = requireExecution(aiRunId, actor);
        AnalysisInput nextInput = mergeAnswers(current.latestInput(), normalizedAnswers);
        UUID attemptId = uuidGenerator.generate();
        Instant now = clock.instant();
        ExecutionState started = inTenant(actor.companyId(), () -> {
            ExecutionState state = repository.startAnswerAttempt(
                    aiRunId,
                    actor.companyId(),
                    actor.actorId(),
                    expectedVersion,
                    normalizedAnswers,
                    attemptId,
                    nextInput,
                    now
            );
            appendAudit(
                    aiRunId,
                    actor,
                    AuditAction.AI_RUN_ANSWERS_SUBMITTED,
                    "AI 분석에 필요한 추가 정보를 제출함",
                    metadata,
                    now
            );
            return state;
        });
        publishCurrent(started.aiRunId(), started.companyId());
        executeOne(started, request(
                started.requestId(),
                attemptId,
                AiAnalysisPhase.ANALYZE,
                nextInput
        ));
        return requireRun(aiRunId, actor);
    }

    @Override
    public UUID startAttempt(
            UUID companyId,
            UUID requestId,
            AiAnalysisPhase phase,
            int contextRound,
            AnalysisInput analysisInput
    ) {
        UUID attemptId = uuidGenerator.generate();
        ExecutionState started = inTenant(companyId, () -> repository.startContinuationAttempt(
                requestId,
                attemptId,
                phase,
                contextRound,
                analysisInput,
                clock.instant()
        ));
        publishCurrent(started.aiRunId(), started.companyId());
        return attemptId;
    }

    private AiRunCreation createPlan(
            String instruction,
            String instructionHash,
            String keyHash,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        UUID aiRunId = uuidGenerator.generate();
        UUID requestId = uuidGenerator.generate();
        UUID attemptId = uuidGenerator.generate();
        AnalysisInput input = new AnalysisInput(
                instruction,
                Map.of(),
                List.of(),
                List.of(),
                List.of()
        );
        AiAnalysisRequest request = request(requestId, attemptId, AiAnalysisPhase.PLAN, input);
        try {
            return inTenant(actor.companyId(), () -> createPlanInTransaction(
                    instruction,
                    instructionHash,
                    keyHash,
                    actor,
                    aiRunId,
                    requestId,
                    attemptId,
                    input,
                    request,
                    metadata
            ));
        } catch (DataIntegrityViolationException exception) {
            return inTenant(actor.companyId(), () -> recoverIdempotentRace(
                    actor.companyId(),
                    instructionHash,
                    keyHash,
                    request,
                    exception
            ));
        }
    }

    private AiRunCreation createPlanInTransaction(
            String instruction,
            String instructionHash,
            String keyHash,
            ActorContext actor,
            UUID aiRunId,
            UUID requestId,
            UUID attemptId,
            AnalysisInput input,
            AiAnalysisRequest request,
            RequestMetadata metadata
    ) {
        var existing = repository.findByIdempotencyKeyHash(actor.companyId(), keyHash);
        if (existing.isPresent()) {
            if (!existing.get().instructionHash().equals(instructionHash)) {
                throw new ApiException(AiRunErrorCode.AI_RUN_IDEMPOTENCY_CONFLICT);
            }
            return new AiRunCreation(existing.get().aiRunId(), actor.companyId(), request, false);
        }
        repository.insertPlan(new AiRunRepository.PlanRun(
                aiRunId,
                actor.companyId(),
                actor.actorId(),
                requestId,
                attemptId,
                instruction,
                instructionHash,
                keyHash,
                input,
                clock.instant()
        ));
        appendAudit(
                aiRunId,
                actor,
                AuditAction.AI_RUN_CREATED,
                "AI 업무 분석 요청을 생성함",
                metadata,
                clock.instant()
        );
        return new AiRunCreation(aiRunId, actor.companyId(), request, true);
    }

    private AiRunCreation recoverIdempotentRace(
            UUID companyId,
            String instructionHash,
            String keyHash,
            AiAnalysisRequest request,
            DataIntegrityViolationException originalFailure
    ) {
        var raced = repository.findByIdempotencyKeyHash(companyId, keyHash)
                .orElseThrow(() -> originalFailure);
        if (!raced.instructionHash().equals(instructionHash)) {
            throw new ApiException(AiRunErrorCode.AI_RUN_IDEMPOTENCY_CONFLICT);
        }
        return new AiRunCreation(raced.aiRunId(), companyId, request, false);
    }

    private void executePlan(AiRunCreation creation) {
        ExecutionState initial = inTenant(creation.companyId(), () -> repository
                .findExecutionState(creation.aiRunId(), creation.companyId())
                .orElseThrow(() -> new IllegalStateException("created AI Run has no attempt")));
        try {
            initial = inTenant(creation.companyId(), () -> repository.startInitialAttempt(
                    creation.aiRunId(),
                    creation.companyId(),
                    clock.instant()
            ));
            publishCurrent(initial.aiRunId(), initial.companyId());
            AiAnalysisResponse planResponse = runtimeClient.analyze(
                    creation.request(),
                    AiRuntimeCallContext.withoutTrace()
            );
            saveSuccess(creation.aiRunId(), creation.companyId(), creation.request().attemptId(), planResponse);
            if (planResponse.outcome() == AiAnalysisOutcome.CONTEXT_REQUIRED) {
                AiAnalysisContinuationResult result = new AiAnalysisContinuationService(
                        slotResolutionTransaction,
                        this,
                        runtimeClient
                ).continueAnalysis(
                        creation.companyId(),
                        creation.request(),
                        planResponse,
                        0,
                        runtimeDeadlinePolicy.attemptDeadlineMs(),
                        AiRuntimeCallContext.withoutTrace()
                );
                saveSuccess(
                        creation.aiRunId(),
                        creation.companyId(),
                        result.attemptId(),
                        result.response()
                );
            }
        } catch (RuntimeException exception) {
            markLatestFailed(initial, exception);
        }
    }

    private void schedulePlan(AiRunCreation creation) {
        try {
            aiRunTaskExecutor.execute(() -> executePlan(creation));
        } catch (RuntimeException schedulingFailure) {
            ExecutionState queued = inTenant(creation.companyId(), () -> repository
                    .findExecutionState(creation.aiRunId(), creation.companyId())
                    .orElseThrow(() -> new IllegalStateException("queued AI Run has no attempt")));
            markLatestFailed(queued, schedulingFailure);
        }
    }

    private void executeOne(ExecutionState state, AiAnalysisRequest request) {
        try {
            AiAnalysisResponse response = runtimeClient.analyze(
                    request,
                    AiRuntimeCallContext.withoutTrace()
            );
            saveSuccess(state.aiRunId(), state.companyId(), request.attemptId(), response);
        } catch (RuntimeException exception) {
            markLatestFailed(state, exception);
        }
    }

    private void saveSuccess(
            UUID aiRunId,
            UUID companyId,
            UUID attemptId,
            AiAnalysisResponse response
    ) {
        inTenant(companyId, () -> {
            repository.markAttemptSucceeded(aiRunId, companyId, attemptId, response, clock.instant());
            return null;
        });
        publishCurrent(aiRunId, companyId);
    }

    private void markLatestFailed(ExecutionState fallback, RuntimeException failure) {
        ExecutionState latest = inTenant(fallback.companyId(), () -> repository
                .findExecutionState(fallback.aiRunId(), fallback.companyId())
                .orElse(fallback));
        inTenant(latest.companyId(), () -> {
            repository.markAttemptFailed(
                    latest.aiRunId(),
                    latest.companyId(),
                    latest.latestAttemptId(),
                    failureCode(failure),
                    clock.instant()
            );
            return null;
        });
        publishCurrent(latest.aiRunId(), latest.companyId());
    }

    private ExecutionState requireExecution(UUID aiRunId, ActorContext actor) {
        return inTenant(actor.companyId(), () -> repository
                .findExecutionState(aiRunId, actor.companyId())
                .orElseThrow(() -> new ApiException(AiRunErrorCode.AI_RUN_NOT_FOUND)));
    }

    private AiAnalysisRequest request(
            UUID requestId,
            UUID attemptId,
            AiAnalysisPhase phase,
            AnalysisInput input
    ) {
        return new AiAnalysisRequest(
                requestId,
                attemptId,
                phase,
                CONTRACT_VERSION,
                workflowCatalogService.getActiveCatalog().bundleVersion(),
                runtimeDeadlinePolicy.attemptDeadlineMs(),
                input
        );
    }

    private AnalysisInput mergeAnswers(AnalysisInput previous, Map<String, String> answers) {
        if (previous.workers().isEmpty()) {
            throw new ApiException(AiRunErrorCode.AI_RUN_ANSWERS_NOT_ALLOWED);
        }
        WorkerContext worker = previous.workers().get(0);
        Map<String, String> fields = new LinkedHashMap<>(worker.requestedFields());
        fields.putAll(answers);
        LinkedHashSet<String> fieldKeys = new LinkedHashSet<>(previous.requestedFieldKeys());
        fieldKeys.addAll(answers.keySet());
        WorkerContext mergedWorker = new WorkerContext(
                worker.workerRef(),
                worker.displayName(),
                worker.nationalityCode(),
                worker.preferredLanguage(),
                worker.workStatus(),
                worker.stayExpiryDate(),
                worker.contractStartDate(),
                worker.contractEndDate(),
                fields
        );
        return new AnalysisInput(
                previous.instruction(),
                previous.extractedSlots(),
                new ArrayList<>(fieldKeys),
                List.of(mergedWorker),
                previous.workflowConstraints(),
                previous.plannedIntentDecision()
        );
    }

    private Map<String, String> normalizeAnswers(Map<String, String> answers) {
        if (answers == null || answers.isEmpty() || answers.size() > 50) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_ANSWER);
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        answers.forEach((key, value) -> {
            if (key == null || !SLOT_KEY.matcher(key).matches()
                    || value == null || value.isBlank() || value.length() > 2_000) {
                throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_ANSWER);
            }
            normalized.put(key, value.trim());
        });
        return Map.copyOf(normalized);
    }

    private String normalizeInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_INSTRUCTION);
        }
        String normalized = instruction.trim();
        if (normalized.length() > MAX_INSTRUCTION_LENGTH) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_INSTRUCTION);
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_IDEMPOTENCY_KEY);
        }
        return key.trim();
    }

    private String failureCode(RuntimeException failure) {
        if (failure instanceof AiRuntimeCallException runtimeFailure) {
            return runtimeFailure.failureCode().name();
        }
        if (failure instanceof AiRuntimeContractException contractFailure) {
            return contractFailure.failureCode().name();
        }
        if (failure instanceof AiContextResolutionException contextFailure) {
            return contextFailure.failureCode().name();
        }
        return "UNEXPECTED_AI_RUN_FAILURE";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private <T> T inTenant(UUID companyId, Supplier<T> action) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return transactionTemplate.execute(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
            return action.get();
        });
    }

    private void publishCurrent(UUID aiRunId, UUID companyId) {
        AiRunResult current = inTenant(companyId, () -> repository
                .findByIdAndCompanyId(aiRunId, companyId)
                .orElseThrow(() -> new IllegalStateException("AI Run for public event was not found")));
        publicEventPublisher.publish(companyId, current);
    }

    private void appendAudit(
            UUID aiRunId,
            ActorContext actor,
            AuditAction action,
            String summary,
            RequestMetadata metadata,
            Instant now
    ) {
        auditEventRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                action,
                AuditTargetType.AI_RUN,
                aiRunId,
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                summary,
                now
        ));
    }

    private UserRole effectiveRole(ActorContext actor) {
        return actor.roles().stream()
                .min(Comparator.comparingInt(role -> switch (role) {
                    case ADMIN -> 0;
                    case HR -> 1;
                    case VIEWER -> 2;
                }))
                .orElseThrow();
    }
}
