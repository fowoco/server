package com.fowoco.server.airun.application.port;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.airun.application.AiCandidateDecisionResult;
import com.fowoco.server.airun.application.AiRunCandidateResult;
import com.fowoco.server.airun.domain.AiCandidateDecisionAction;
import com.fowoco.server.airun.domain.AiRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiCandidateDecisionRepository {

    DecisionContext lockRun(UUID aiRunId, UUID companyId);

    Optional<StoredBatch> findBatch(
            UUID aiRunId,
            UUID companyId,
            String idempotencyKeyHash
    );

    boolean candidateAlreadyDecided(UUID candidateId, UUID companyId);

    void insertBatch(NewBatch batch);

    void insertDecision(NewDecision decision);

    void attachTasks(
            UUID decisionId,
            UUID companyId,
            List<UUID> taskIds,
            Instant createdAt
    );

    long completeBatch(
            UUID decisionBatchId,
            UUID aiRunId,
            UUID companyId,
            UUID caseId,
            long expectedRunVersion,
            Instant completedAt
    );

    record DecisionContext(
            UUID aiRunId,
            UUID companyId,
            AiRunStatus status,
            AiAnalysisOutcome outcome,
            String detectedIntent,
            long version,
            List<AiRunCandidateResult> candidates
    ) {
        public DecisionContext {
            candidates = List.copyOf(candidates);
        }
    }

    record StoredBatch(
            String payloadHash,
            AiCandidateDecisionResult result
    ) {
    }

    record NewBatch(
            UUID decisionBatchId,
            UUID aiRunId,
            UUID companyId,
            UUID decidedBy,
            String idempotencyKeyHash,
            String payloadHash,
            Instant createdAt
    ) {
    }

    record NewDecision(
            UUID decisionId,
            UUID decisionBatchId,
            UUID aiRunId,
            UUID candidateId,
            UUID companyId,
            AiCandidateDecisionAction action,
            Instant createdAt
    ) {
    }
}
