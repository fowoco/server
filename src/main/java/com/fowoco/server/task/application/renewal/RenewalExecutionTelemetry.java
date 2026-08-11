package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
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
 * Records only Server-observable Renewal stages. Raw instructions, Slot values and Provider
 * responses must never be included in this log.
 */
@Component
final class RenewalExecutionTelemetry {

    private static final Logger log = LoggerFactory.getLogger(RenewalExecutionTelemetry.class);

    private final MeterRegistry meterRegistry;

    RenewalExecutionTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    <T> T measure(
            UUID requestId,
            String httpRequestId,
            Stage stage,
            Supplier<T> action
    ) {
        long startedNanos = System.nanoTime();
        try {
            T result = action.get();
            long elapsedNanos = elapsedNanos(startedNanos);
            recordStage(stage, Status.SUCCESS, elapsedNanos);
            log.info(
                    "event=renewal_execution_stage request_id={} http_request_id={} stage={} "
                            + "status=SUCCESS duration_ms={}",
                    requestId,
                    httpRequestId,
                    stage,
                    toMillis(elapsedNanos)
            );
            return result;
        } catch (RuntimeException exception) {
            long elapsedNanos = elapsedNanos(startedNanos);
            String failureCode = errorCode(exception);
            recordStage(stage, Status.FAILED, elapsedNanos);
            recordFailure(stage, failureCode);
            log.warn(
                    "event=renewal_execution_stage request_id={} http_request_id={} stage={} "
                            + "status=FAILED duration_ms={} error_code={}",
                    requestId,
                    httpRequestId,
                    stage,
                    toMillis(elapsedNanos),
                    failureCode
            );
            throw exception;
        }
    }

    private void recordStage(Stage stage, Status status, long elapsedNanos) {
        try {
            Timer.builder("fowoco.renewal.stage")
                    .description("Server-observed Renewal execution stage duration")
                    .tag("stage", stage.name())
                    .tag("status", status.name())
                    .register(meterRegistry)
                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
        } catch (RuntimeException metricFailure) {
            log.warn(
                    "event=renewal_metric status=FAILED metric=execution_stage stage={}",
                    stage
            );
        }
    }

    private void recordFailure(Stage stage, String failureCode) {
        try {
            Counter.builder("fowoco.renewal.failures")
                    .description("Server-observed Renewal execution stage failures")
                    .tag("stage", stage.name())
                    .tag("failure_code", failureCode)
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException metricFailure) {
            log.warn("event=renewal_metric status=FAILED metric=failures");
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
        if (exception instanceof ApiException apiFailure) {
            return apiFailure.errorCode().code();
        }
        return "UNEXPECTED_ERROR";
    }

    enum Stage {
        CONTEXT_LOAD,
        RENEWAL_RUNTIME_CALL,
        DOCUMENT_GENERATION,
        RESULT_APPLY,
        TOTAL
    }

    private enum Status {
        SUCCESS,
        FAILED
    }
}
