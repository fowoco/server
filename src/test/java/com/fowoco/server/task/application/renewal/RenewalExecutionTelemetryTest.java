package com.fowoco.server.task.application.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RenewalExecutionTelemetryTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );
    private static final String HTTP_REQUEST_ID = "renewal-http-request-1";
    private static final UUID TASK_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final String SENSITIVE_FAILURE_MESSAGE =
            "NGUYEN VAN AN passport_number=M12345678";

    private final Logger logger = (Logger) LoggerFactory.getLogger(RenewalExecutionTelemetry.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RenewalExecutionTelemetry telemetry = new RenewalExecutionTelemetry(meterRegistry);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void failureLogContainsOnlyStableCodeAndNeverTheExceptionMessage() {
        assertThatThrownBy(() -> telemetry.measure(
                REQUEST_ID,
                HTTP_REQUEST_ID,
                RenewalExecutionTelemetry.Stage.RENEWAL_RUNTIME_CALL,
                () -> {
                    throw new AiRuntimeCallException(
                            AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                            SENSITIVE_FAILURE_MESSAGE
                    );
                }
        )).isInstanceOf(AiRuntimeCallException.class);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage())
                .contains(
                        "request_id=" + REQUEST_ID,
                        "http_request_id=" + HTTP_REQUEST_ID,
                        "stage=RENEWAL_RUNTIME_CALL",
                        "status=FAILED",
                        "error_code=DEADLINE_EXCEEDED"
                )
                .doesNotContain(
                        "task_id",
                        TASK_ID.toString(),
                        "NGUYEN VAN AN",
                        "passport_number",
                        "M12345678"
                );
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(meterRegistry.get("fowoco.renewal.stage")
                .tag("stage", "RENEWAL_RUNTIME_CALL")
                .tag("status", "FAILED")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("fowoco.renewal.failures")
                .tag("stage", "RENEWAL_RUNTIME_CALL")
                .tag("failure_code", "DEADLINE_EXCEEDED")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void successRecordsDurationWithoutResourceIdentifiers() {
        String result = telemetry.measure(
                REQUEST_ID,
                HTTP_REQUEST_ID,
                RenewalExecutionTelemetry.Stage.CONTEXT_LOAD,
                () -> "loaded"
        );

        assertThat(result).isEqualTo("loaded");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("stage=CONTEXT_LOAD", "status=SUCCESS", "duration_ms=")
                .doesNotContain("task_id", TASK_ID.toString());
        assertThat(meterRegistry.get("fowoco.renewal.stage")
                .tag("stage", "CONTEXT_LOAD")
                .tag("status", "SUCCESS")
                .timer()
                .count()).isEqualTo(1);
    }
}
