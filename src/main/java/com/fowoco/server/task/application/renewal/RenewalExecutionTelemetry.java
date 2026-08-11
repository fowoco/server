package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.common.error.ApiException;
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

    <T> T measure(
            String requestId,
            UUID taskId,
            Stage stage,
            Supplier<T> action
    ) {
        long startedNanos = System.nanoTime();
        try {
            T result = action.get();
            log.info(
                    "event=renewal_execution_stage request_id={} task_id={} stage={} "
                            + "status=SUCCESS duration_ms={}",
                    requestId,
                    taskId,
                    stage,
                    elapsedMillis(startedNanos)
            );
            return result;
        } catch (RuntimeException exception) {
            log.warn(
                    "event=renewal_execution_stage request_id={} task_id={} stage={} "
                            + "status=FAILED duration_ms={} error_code={}",
                    requestId,
                    taskId,
                    stage,
                    elapsedMillis(startedNanos),
                    errorCode(exception)
            );
            throw exception;
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos)
        );
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
}
