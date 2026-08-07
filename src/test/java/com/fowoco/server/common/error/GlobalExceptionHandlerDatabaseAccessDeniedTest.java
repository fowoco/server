package com.fowoco.server.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerDatabaseAccessDeniedTest {

    private static final String REQUEST_ID =
            "00000000-0000-0000-0000-000000000065";
    private static final String SECRET_PATH = "sensitive-worker-path-token";
    private static final String TABLE = "private_worker_records";
    private static final String POLICY = "private_worker_tenant_policy";
    private static final String TOKEN = "worker-link-secret-token";
    private static final String EMAIL = "private.worker@example.com";
    private static final String SQL = "INSERT INTO " + TABLE + " VALUES (?)";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(
            GlobalExceptionHandler.class
    );
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-08-06T00:00:00Z"),
                ZoneOffset.UTC
        );
        RequestIdFilter requestIdFilter = new RequestIdFilter(
                () -> UUID.fromString(REQUEST_ID)
        );
        logAppender.start();
        handlerLogger.addAppender(logAppender);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccessDeniedTestController())
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
    void confirmedAccessDeniedUsesSafe500ResponseLogAndMetric() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/database/{secret}", SECRET_PATH)
                        .queryParam("failure", "database"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.request_id").value(REQUEST_ID))
                .andExpect(jsonPath("$.path").value("/test/database/{secret}"))
                .andReturn();

        assertSafeResponse(result);
        assertAccessDeniedMetric(1.0);
        assertSafeAccessDeniedLog();
    }

    @Test
    void optimisticLockWrappingAccessDeniedUsesDatabaseAccessDeniedContract()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/test/database/{secret}", SECRET_PATH)
                        .queryParam("failure", "optimistic-database"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.path").value("/test/database/{secret}"))
                .andReturn();

        assertSafeResponse(result);
        assertAccessDeniedMetric(1.0);
        assertSafeAccessDeniedLog();
    }

    @Test
    void springSecurityAccessDeniedKeepsExisting403Contract() throws Exception {
        mockMvc.perform(get("/test/database/{secret}", SECRET_PATH)
                        .queryParam("failure", "security"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertAccessDeniedMetric(0.0);
        assertThat(logAppender.list).isEmpty();
    }

    @Test
    void ordinaryOptimisticLockKeepsExisting409WithoutAccessDeniedMetric()
            throws Exception {
        mockMvc.perform(get("/test/database/{secret}", SECRET_PATH)
                        .queryParam("failure", "optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        assertAccessDeniedMetric(0.0);
        assertThat(logAppender.list).isEmpty();
    }

    private void assertSafeResponse(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(
                        "42501",
                        SQL,
                        TABLE,
                        POLICY,
                        SECRET_PATH,
                        TOKEN,
                        EMAIL,
                        "row-level security"
                );
    }

    private void assertAccessDeniedMetric(double expectedCount) {
        assertThat(meterRegistry.get("fowoco.database.access.denied")
                .counter().count()).isEqualTo(expectedCount);
    }

    private void assertSafeAccessDeniedLog() {
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getFormattedMessage())
                .contains("requestId=" + REQUEST_ID)
                .contains("method=GET")
                .contains("route=/test/database/{secret}")
                .contains("classification=DATABASE_ACCESS_DENIED")
                .contains("sqlState=42501")
                .contains("exceptionType=java.sql.SQLException")
                .doesNotContain(
                        SQL,
                        TABLE,
                        POLICY,
                        SECRET_PATH,
                        TOKEN,
                        EMAIL,
                        "row-level security"
                );
        assertThat(event.getThrowableProxy()).isNull();
    }

    @RestController
    @RequestMapping("/test")
    private static class AccessDeniedTestController {

        @GetMapping("/database/{secret}")
        private void fail(
                @PathVariable String secret,
                @RequestParam String failure
        ) {
            throw switch (failure) {
                case "database" -> new RuntimeException(
                        "database operation failed",
                        accessDeniedSqlException()
                );
                case "optimistic-database" ->
                        new ObjectOptimisticLockingFailureException(
                                "optimistic update failed",
                                accessDeniedSqlException()
                        );
                case "security" -> new AccessDeniedException("business access denied");
                default -> new ObjectOptimisticLockingFailureException(Object.class, 1L);
            };
        }

        private static SQLException accessDeniedSqlException() {
            return new SQLException(
                    "row-level security policy " + POLICY
                            + " rejected table " + TABLE
                            + " sql=" + SQL
                            + " token=" + TOKEN
                            + " email=" + EMAIL,
                    "42501"
            );
        }
    }
}
