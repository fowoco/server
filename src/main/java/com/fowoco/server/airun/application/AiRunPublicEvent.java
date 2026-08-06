package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.airun.domain.AiRunStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Client에 공개해도 되는 실행 상태만 담습니다. 발화문·Prompt·Provider 원문은 포함하지 않습니다.
 */
public record AiRunPublicEvent(
        long eventId,
        UUID aiRunId,
        AiRunPublicEventType type,
        AiRunStatus status,
        AiAnalysisOutcome analysisOutcome,
        int attemptCount,
        long version,
        Instant occurredAt
) {
    public AiRunPublicEvent {
        if (eventId < 0 || version < 0 || attemptCount < 0) {
            throw new IllegalArgumentException("AI Run public event numbers must not be negative");
        }
        Objects.requireNonNull(aiRunId, "aiRunId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AiRunPublicEvent from(AiRunResult run) {
        return new AiRunPublicEvent(
                run.version(),
                run.aiRunId(),
                typeOf(run),
                run.status(),
                run.analysisOutcome(),
                run.attemptCount(),
                run.version(),
                run.updatedAt()
        );
    }

    public boolean terminal() {
        return type == AiRunPublicEventType.NEEDS_INFO
                || type == AiRunPublicEventType.REVIEW_REQUIRED
                || type == AiRunPublicEventType.COMPLETED
                || type == AiRunPublicEventType.FAILED;
    }

    private static AiRunPublicEventType typeOf(AiRunResult run) {
        if (run.status() == AiRunStatus.QUEUED) {
            return AiRunPublicEventType.RUN_QUEUED;
        }
        if (run.status() == AiRunStatus.RUNNING) {
            return run.attemptCount() <= 1
                    ? AiRunPublicEventType.RUN_STARTED
                    : AiRunPublicEventType.SLOT_CHECKING;
        }
        if (run.status() == AiRunStatus.FAILED) {
            return AiRunPublicEventType.FAILED;
        }
        if (run.analysisOutcome() == AiAnalysisOutcome.CONTEXT_REQUIRED) {
            return AiRunPublicEventType.SLOT_CHECKING;
        }
        if (run.analysisOutcome() == AiAnalysisOutcome.NEEDS_INFO) {
            return AiRunPublicEventType.NEEDS_INFO;
        }
        if (run.analysisOutcome() == AiAnalysisOutcome.REVIEW_REQUIRED) {
            return AiRunPublicEventType.REVIEW_REQUIRED;
        }
        return AiRunPublicEventType.COMPLETED;
    }
}
