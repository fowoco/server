package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.airun.application.error.AiRunErrorCode;
import com.fowoco.server.airun.application.port.AiCandidateDecisionRepository;
import com.fowoco.server.airun.application.port.AiCandidateDecisionRepository.DecisionContext;
import com.fowoco.server.airun.application.port.AiCandidateDecisionRepository.NewBatch;
import com.fowoco.server.airun.application.port.AiCandidateDecisionRepository.NewDecision;
import com.fowoco.server.airun.domain.AiCandidateDecisionAction;
import com.fowoco.server.airun.domain.AiRunStatus;
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
import com.fowoco.server.task.application.port.AiCandidateTaskCreator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCandidateDecisionService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final AiCandidateDecisionRepository repository;
    private final AiCandidateTaskCreator taskCreator;
    private final AuditEventRepository auditEventRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public AiCandidateDecisionService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            AiCandidateDecisionRepository repository,
            AiCandidateTaskCreator taskCreator,
            AuditEventRepository auditEventRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.repository = repository;
        this.taskCreator = taskCreator;
        this.auditEventRepository = auditEventRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public AiCandidateDecisionResult decide(
            UUID aiRunId,
            String idempotencyKey,
            AiCandidateDecisionCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        actorAuthorizer.requireHrWrite(actor);
        validateCommand(command);

        String keyHash = sha256(normalizeIdempotencyKey(idempotencyKey));
        String payloadHash = payloadHash(command);
        DecisionContext context = repository.lockRun(aiRunId, actor.companyId());
        var existing = repository.findBatch(aiRunId, actor.companyId(), keyHash);
        if (existing.isPresent()) {
            if (!existing.get().payloadHash().equals(payloadHash)) {
                throw new ApiException(AiRunErrorCode.AI_RUN_IDEMPOTENCY_CONFLICT);
            }
            return existing.get().result();
        }

        validateRun(context, command.expectedRunVersion());
        Map<UUID, AiRunCandidateResult> candidates = context.candidates().stream()
                .collect(Collectors.toMap(AiRunCandidateResult::candidateId, Function.identity()));
        List<ResolvedDecision> resolved = command.decisions().stream()
                .map(decision -> resolve(decision, candidates, actor.companyId()))
                .toList();
        List<ResolvedDecision> accepted = resolved.stream()
                .filter(decision -> decision.action() == AiCandidateDecisionAction.ACCEPT)
                .toList();
        if (accepted.size() > 1) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_DECISION);
        }

        Instant now = clock.instant();
        UUID batchId = uuidGenerator.generate();
        repository.insertBatch(new NewBatch(
                batchId,
                aiRunId,
                actor.companyId(),
                actor.actorId(),
                keyHash,
                payloadHash,
                now
        ));
        List<PersistedDecision> persisted = resolved.stream()
                .map(decision -> persist(batchId, aiRunId, actor.companyId(), decision, now))
                .toList();

        UUID caseId = null;
        List<UUID> taskIds = List.of();
        if (!accepted.isEmpty()) {
            ResolvedDecision acceptedDecision = accepted.get(0);
            if (!acceptedDecision.candidate().missingSlots().isEmpty()) {
                throw new ApiException(AiRunErrorCode.AI_RUN_CANDIDATE_NOT_READY);
            }
            AiCandidateTaskCreator.CreationResult created = taskCreator.create(
                    new AiCandidateTaskCreator.CreationCommand(
                            aiRunId,
                            acceptedDecision.candidate().candidateId(),
                            acceptedDecision.candidate().workerId(),
                            context.detectedIntent(),
                            acceptedDecision.candidate().workflowId(),
                            acceptedDecision.candidate().extractedSlots()
                    ),
                    actor,
                    metadata
            );
            caseId = created.caseId();
            taskIds = created.taskIds();
            UUID acceptedDecisionId = persisted.stream()
                    .filter(decision -> decision.action() == AiCandidateDecisionAction.ACCEPT)
                    .findFirst()
                    .orElseThrow()
                    .decisionId();
            repository.attachTasks(acceptedDecisionId, actor.companyId(), taskIds, now);
        }

        long runVersion = repository.completeBatch(
                batchId,
                aiRunId,
                actor.companyId(),
                caseId,
                command.expectedRunVersion(),
                now
        );
        appendAudit(aiRunId, actor, metadata, now, accepted.isEmpty(), taskIds.size());
        return new AiCandidateDecisionResult(
                batchId,
                aiRunId,
                caseId,
                taskIds,
                persisted.stream()
                        .map(decision -> new AiCandidateDecisionResult.Decision(
                                decision.candidateId(),
                                decision.action()
                        ))
                        .toList(),
                runVersion
        );
    }

    private void validateCommand(AiCandidateDecisionCommand command) {
        if (command.decisions().isEmpty() || command.decisions().size() > 20) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_DECISION);
        }
        HashSet<UUID> candidateIds = new HashSet<>();
        if (command.decisions().stream().anyMatch(decision -> !candidateIds.add(decision.candidateId()))) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_DECISION);
        }
    }

    private void validateRun(DecisionContext context, long expectedVersion) {
        if (context.version() != expectedVersion) {
            throw new ApiException(AiRunErrorCode.AI_RUN_VERSION_CONFLICT);
        }
        if (context.status() != AiRunStatus.SUCCEEDED
                || context.outcome() != AiAnalysisOutcome.REVIEW_REQUIRED
                || context.detectedIntent() == null
                || context.candidates().isEmpty()) {
            throw new ApiException(AiRunErrorCode.AI_RUN_DECISION_NOT_ALLOWED);
        }
    }

    private ResolvedDecision resolve(
            AiCandidateDecisionCommand.Decision decision,
            Map<UUID, AiRunCandidateResult> candidates,
            UUID companyId
    ) {
        AiRunCandidateResult candidate = candidates.get(decision.candidateId());
        if (candidate == null) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_DECISION);
        }
        if (repository.candidateAlreadyDecided(candidate.candidateId(), companyId)) {
            throw new ApiException(AiRunErrorCode.AI_RUN_CANDIDATE_ALREADY_DECIDED);
        }
        return new ResolvedDecision(candidate, decision.action());
    }

    private PersistedDecision persist(
            UUID batchId,
            UUID aiRunId,
            UUID companyId,
            ResolvedDecision decision,
            Instant now
    ) {
        UUID decisionId = uuidGenerator.generate();
        repository.insertDecision(new NewDecision(
                decisionId,
                batchId,
                aiRunId,
                decision.candidate().candidateId(),
                companyId,
                decision.action(),
                now
        ));
        return new PersistedDecision(
                decisionId,
                decision.candidate().candidateId(),
                decision.action()
        );
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_IDEMPOTENCY_KEY);
        }
        return key.trim();
    }

    private String payloadHash(AiCandidateDecisionCommand command) {
        String decisions = command.decisions().stream()
                .sorted(Comparator.comparing(decision -> decision.candidateId().toString()))
                .map(decision -> decision.candidateId() + ":" + decision.action())
                .collect(Collectors.joining("|"));
        return sha256(command.expectedRunVersion() + "|" + decisions);
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

    private void appendAudit(
            UUID aiRunId,
            ActorContext actor,
            RequestMetadata metadata,
            Instant now,
            boolean discardedOnly,
            int taskCount
    ) {
        auditEventRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                AuditAction.AI_RUN_CANDIDATES_DECIDED,
                AuditTargetType.AI_RUN,
                aiRunId,
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                discardedOnly
                        ? "AI 업무 후보를 폐기함"
                        : "AI 업무 후보를 채택하고 업무카드 " + taskCount + "건을 생성함",
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

    private record ResolvedDecision(
            AiRunCandidateResult candidate,
            AiCandidateDecisionAction action
    ) {
    }

    private record PersistedDecision(
            UUID decisionId,
            UUID candidateId,
            AiCandidateDecisionAction action
    ) {
    }
}
