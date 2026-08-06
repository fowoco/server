package com.fowoco.server.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fowoco.server.common.database.DatabaseAccessDeniedMetrics;
import com.fowoco.server.common.database.DatabaseTimeoutMetrics;
import com.fowoco.server.common.database.PostgreSqlAccessDeniedClassifier;
import com.fowoco.server.common.database.PostgreSqlTimeoutClassifier;
import com.fowoco.server.common.web.RequestIdFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerDatabaseTimeoutTest {

    private static final String REQUEST_ID =
            "00000000-0000-0000-0000-000000000064";
    private static final String SECRET = "worker-link-secret-token";
    private static final String SQL = "SELECT private_value FROM secret_table";
    private static final String PARAMETER = "passport-number-parameter";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(
            GlobalExceptionHandler.class
    );
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-07-31T00:00:00Z"),
                ZoneOffset.UTC
        );
        RequestIdFilter requestIdFilter = new RequestIdFilter(
                () -> UUID.fromString(REQUEST_ID)
        );
        logAppender.start();
        handlerLogger.addAppender(logAppender);
        mockMvc = MockMvcBuilders.standaloneSetup(new TimeoutTestController())
                .setControllerAdvice(new GlobalExceptionHandler(
                        fixedClock,
                        new PostgreSqlAccessDeniedClassifier(),
                        new DatabaseAccessDeniedMetrics(meterRegistry),
                        new PostgreSqlTimeoutClassifier(),
                        new DatabaseTimeoutMetrics(meterRegistry)
                ))
                .addFilters(requestIdFilter)
                .build();
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logAppender);
        logAppender.stop();
        meterRegistry.close();
    }

    @Test
    void confirmedStatementTimeoutUsesSafe503ResponseLogAndMetric() throws Exception {
        mockMvc.perform(get("/test/sensitive/{secret}", SECRET)
                        .queryParam("failure", "statement"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("SERVICE_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.path")
                        .value("/test/sensitive/{secret}"));

        assertThat(meterRegistry.get("fowoco.database.timeouts")
                .tag("type", "statement").counter().count()).isEqualTo(1.0);
        assertSafeLog("DATABASE_STATEMENT_TIMEOUT", "57014");
    }

    @Test
    void confirmedLockTimeoutUses503AndLockMetric() throws Exception {
        mockMvc.perform(get("/test/sensitive/{secret}", SECRET)
                        .queryParam("failure", "lock"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("SERVICE_TEMPORARILY_UNAVAILABLE"));

        assertThat(meterRegistry.get("fowoco.database.timeouts")
                .tag("type", "lock").counter().count()).isEqualTo(1.0);
        assertSafeLog("DATABASE_LOCK_TIMEOUT", "55P03");
    }

    @Test
    void ambiguousCancellationUsesSafe500WithoutTimeoutMetric() throws Exception {
        mockMvc.perform(get("/test/sensitive/{secret}", SECRET)
                        .queryParam("failure", "ambiguous"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.path")
                        .value("/test/sensitive/{secret}"));

        assertThat(meterRegistry.find("fowoco.database.timeouts")
                .tag("type", "statement").counter().count()).isZero();
        assertSafeLog("AMBIGUOUS_QUERY_CANCELED", "57014");
    }

    @Test
    void ordinaryUnexpectedFailureKeepsExistingHandling() throws Exception {
        mockMvc.perform(get("/test/sensitive/{secret}", SECRET)
                        .queryParam("failure", "ordinary"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getThrowableProxy()).isNotNull();
    }

    private void assertSafeLog(String classification, String sqlState) {
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        String message = event.getFormattedMessage();
        assertThat(message)
                .contains("requestId=" + REQUEST_ID)
                .contains("method=GET")
                .contains("route=/test/sensitive/{secret}")
                .contains("classification=" + classification)
                .contains("sqlState=" + sqlState)
                .contains("exceptionType=java.sql.SQLException")
                .doesNotContain(SECRET, SQL, PARAMETER, "canceling statement");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @RestController
    @RequestMapping("/test")
    private static class TimeoutTestController {

        @GetMapping("/sensitive/{secret}")
        private void fail(
                @PathVariable String secret,
                @RequestParam String failure
        ) {
            throw switch (failure) {
                case "statement" -> wrappedSqlFailure(
                        "canceling statement due to statement timeout",
                        "57014"
                );
                case "lock" -> wrappedSqlFailure(
                        "canceling statement due to lock timeout",
                        "55P03"
                );
                case "ambiguous" -> wrappedSqlFailure(
                        "canceling statement due to user request",
                        "57014"
                );
                default -> new IllegalStateException("ordinary failure");
            };
        }

        private RuntimeException wrappedSqlFailure(String diagnostic, String sqlState) {
            SQLException sqlException = new SQLException(
                    diagnostic + " sql=" + SQL + " parameter=" + PARAMETER,
                    sqlState
            );
            return new RuntimeException("database operation failed", sqlException);
        }
    }
}
