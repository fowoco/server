package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import com.fowoco.server.airun.application.error.AiContextResolutionException;
import com.fowoco.server.common.error.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records only Server-observable AiRun stages. Instructions, Slot values and Runtime response
 * bodies must never be included in logs or metric tags.
 */
@Component
final class AiRunExecutionTelemetry {

    private static final Logger log = LoggerFactory.getLogger(AiRunExecutionTelemetry.class);

    private final MeterRegistry meterRegistry;

    AiRunExecutionTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    <T> T measure(
            UUID requestId,
            UUID attemptId,
            Phase phase,
            Stage stage,
            Supplier<T> action
    ) {
        long startedNanos = System.nanoTime();
        try {
            T result = action.get();
            long elapsedNanos = elapsedNanos(startedNanos);
            recordStage(phase, stage, Status.SUCCESS, elapsedNanos);
            log.info(
                    "event=ai_run_stage request_id={} attempt_id={} phase={} stage={} "
                            + "status=SUCCESS duration_ms={}",
                    requestId,
                    attemptId,
                    phase,
                    stage,
                    toMillis(elapsedNanos)
            );
            return result;
        } catch (RuntimeException exception) {
            long elapsedNanos = elapsedNanos(startedNanos);
            String failureCode = errorCode(exception);
            recordStage(phase, stage, Status.FAILED, elapsedNanos);
            recordFailure(phase, stage, failureCode);
            log.warn(
                    "event=ai_run_stage request_id={} attempt_id={} phase={} stage={} "
                            + "status=FAILED duration_ms={} error_code={}",
                    requestId,
                    attemptId,
                    phase,
                    stage,
                    toMillis(elapsedNanos),
                    failureCode
            );
            throw exception;
        }
    }

    void recordOutcome(AiAnalysisOutcome outcome) {
        try {
            Counter.builder("fowoco.ai.analysis.outcomes")
                    .description("Validated AI analysis response outcomes")
                    .tag("outcome", outcome.name())
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException metricFailure) {
            log.warn("event=ai_run_metric status=FAILED metric=analysis_outcomes");
        }
    }

    private void recordStage(Phase phase, Stage stage, Status status, long elapsedNanos) {
        try {
            Timer.builder("fowoco.ai.pipeline.stage")
                    .description("Server-observed AiRun pipeline stage duration")
                    .tag("phase", phase.name())
                    .tag("stage", stage.name())
                    .tag("status", status.name())
                    .register(meterRegistry)
                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException metricFailure) {
            log.warn(
                    "event=ai_run_metric status=FAILED metric=pipeline_stage phase={} stage={}",
                    phase,
                    stage
            );
        }
    }

    private void recordFailure(Phase phase, Stage stage, String failureCode) {
        try {
            Counter.builder("fowoco.ai.pipeline.failures")
                    .description("Server-observed AI pipeline stage failures")
                    .tag("phase", phase.name())
                    .tag("stage", stage.name())
                    .tag("failure_code", failureCode)
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException metricFailure) {
            log.warn("event=ai_run_metric status=FAILED metric=pipeline_failures");
        }
    }

    private long elapsedNanos(long startedNanos) {
        return Math.max(0L, System.nanoTime() - startedNanos);
    }

    private long toMillis(long elapsedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof AiRuntimeCallException runtimeFailure) {
            return runtimeFailure.failureCode().name();
        }
        if (exception instanceof AiRuntimeContractException contractFailure) {
            return contractFailure.failureCode().name();
        }
        if (exception instanceof AiContextResolutionException contextFailure) {
            return contextFailure.failureCode().name();
        }
        if (exception instanceof ApiException apiFailure) {
            return apiFailure.errorCode().code();
        }
        return "UNEXPECTED_AI_RUN_FAILURE";
    }

    enum Phase {
        PIPELINE,
        PLAN,
        ANALYZE
    }

    enum Stage {
        PLAN_RUNTIME_CALL,
        SLOT_RESOLUTION,
        ANALYZE_RUNTIME_CALL,
        RESULT_PERSIST,
        TOTAL
    }

    private enum Status {
        SUCCESS,
        FAILED
    }
}
