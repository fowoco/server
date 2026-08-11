package com.fowoco.server.airun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AiRunExecutionTelemetryTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID ATTEMPT_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );
    private static final String SENSITIVE_FAILURE_MESSAGE =
            "NGUYEN VAN AN passport_number=M12345678 instruction=체류연장";

    private final Logger logger = (Logger) LoggerFactory.getLogger(AiRunExecutionTelemetry.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AiRunExecutionTelemetry telemetry = new AiRunExecutionTelemetry(meterRegistry);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
        meterRegistry.close();
    }

    @Test
    void successRecordsSafeStructuredLogAndTimer() {
        String result = telemetry.measure(
                REQUEST_ID,
                ATTEMPT_ID,
                AiRunExecutionTelemetry.Phase.PLAN,
                AiRunExecutionTelemetry.Stage.PLAN_RUNTIME_CALL,
                () -> "ok"
        );

        assertThat(result).isEqualTo("ok");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains(
                        "request_id=" + REQUEST_ID,
                        "attempt_id=" + ATTEMPT_ID,
                        "phase=PLAN",
                        "stage=PLAN_RUNTIME_CALL",
                        "status=SUCCESS",
                        "duration_ms="
                );
        assertThat(meterRegistry.get("fowoco.ai.pipeline.stage")
                .tag("phase", "PLAN")
                .tag("stage", "PLAN_RUNTIME_CALL")
                .tag("status", "SUCCESS")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void failureRecordsOnlyStableCodeAndNeverSensitiveMessage() {
        assertThatThrownBy(() -> telemetry.measure(
                REQUEST_ID,
                ATTEMPT_ID,
                AiRunExecutionTelemetry.Phase.ANALYZE,
                AiRunExecutionTelemetry.Stage.ANALYZE_RUNTIME_CALL,
                () -> {
                    throw new AiRuntimeCallException(
                            AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                            SENSITIVE_FAILURE_MESSAGE
                    );
                }
        )).isInstanceOf(AiRuntimeCallException.class);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains(
                        "phase=ANALYZE",
                        "stage=ANALYZE_RUNTIME_CALL",
                        "status=FAILED",
                        "error_code=DEADLINE_EXCEEDED"
                )
                .doesNotContain(
                        "NGUYEN VAN AN",
                        "passport_number",
                        "M12345678",
                        "체류연장"
                );
        assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        assertThat(meterRegistry.get("fowoco.ai.pipeline.failures")
                .tag("phase", "ANALYZE")
                .tag("stage", "ANALYZE_RUNTIME_CALL")
                .tag("failure_code", "DEADLINE_EXCEEDED")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void outcomeCounterUsesOnlyBoundedOutcomeTag() {
        telemetry.recordOutcome(AiAnalysisOutcome.REVIEW_REQUIRED);

        assertThat(meterRegistry.get("fowoco.ai.analysis.outcomes")
                .tag("outcome", "REVIEW_REQUIRED")
                .counter()
                .count()).isEqualTo(1);
    }
}
