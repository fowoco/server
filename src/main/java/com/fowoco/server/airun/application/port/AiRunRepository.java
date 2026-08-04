package com.fowoco.server.airun.application.port;

import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import com.fowoco.server.airun.application.AiRunResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AiRunRepository {

    Optional<IdempotentRun> findByIdempotencyKeyHash(UUID companyId, String keyHash);

    void insertPlan(PlanRun run);

    Optional<AiRunResult> findByIdAndCompanyId(UUID aiRunId, UUID companyId);

    Optional<ExecutionState> findExecutionState(UUID aiRunId, UUID companyId);

    Optional<ExecutionState> findExecutionStateByRequestId(UUID requestId);

    void insertAttempt(Attempt attempt);

    ExecutionState startContinuationAttempt(
            UUID requestId,
            UUID attemptId,
            AiAnalysisPhase phase,
            int contextRound,
            AnalysisInput input,
            Instant startedAt
    );

    void markAttemptSucceeded(
            UUID aiRunId,
            UUID companyId,
            UUID attemptId,
            AiAnalysisResponse response,
            Instant completedAt
    );

    void markAttemptFailed(
            UUID aiRunId,
            UUID companyId,
            UUID attemptId,
            String errorCode,
            Instant completedAt
    );

    ExecutionState startAnswerAttempt(
            UUID aiRunId,
            UUID companyId,
            UUID actorId,
            long expectedVersion,
            Map<String, String> answers,
            UUID attemptId,
            AnalysisInput input,
            Instant startedAt
    );

    record IdempotentRun(UUID aiRunId, UUID requestId, String instructionHash) {
    }

    record PlanRun(
            UUID aiRunId,
            UUID companyId,
            UUID actorId,
            UUID requestId,
            UUID attemptId,
            String instruction,
            String instructionHash,
            String idempotencyKeyHash,
            AnalysisInput input,
            Instant createdAt
    ) {
    }

    record Attempt(
            UUID attemptId,
            UUID aiRunId,
            UUID companyId,
            UUID requestId,
            int sequenceNo,
            AiAnalysisPhase phase,
            int contextRound,
            AnalysisInput input,
            Instant startedAt
    ) {
    }

    record ExecutionState(
            UUID aiRunId,
            UUID companyId,
            UUID requestId,
            UUID latestAttemptId,
            int attemptCount,
            int contextRound,
            long version,
            AnalysisInput latestInput
    ) {
    }
}
