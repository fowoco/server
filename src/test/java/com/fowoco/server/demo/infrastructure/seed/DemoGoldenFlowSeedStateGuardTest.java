package com.fowoco.server.demo.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoGoldenFlowSeedStateGuardTest {

    private static final DemoOperationalSeedContext CONTEXT = new DemoOperationalSeedContext(
            UUID.fromString("90000000-0000-0000-0000-000000000001"),
            UUID.fromString("90000000-0000-0000-0000-000000000002"),
            LocalDate.of(2026, 8, 7),
            Instant.parse("2026-08-07T00:00:00Z")
    );

    @Test
    void allowsEmptyOrCurrentVersionDemoDatabase() {
        DemoGoldenFlowSeedStateGuard guard = new DemoGoldenFlowSeedStateGuard(
                jdbcTemplateReturning(0, 0, 0, 0)
        );

        assertThatCode(() -> guard.verifyNoLegacyRows(CONTEXT)).doesNotThrowAnyException();
    }

    @Test
    void rejectsLegacyGoldenFlowReservedRowsWithoutDeletingThem() {
        DemoGoldenFlowSeedStateGuard guard = new DemoGoldenFlowSeedStateGuard(
                jdbcTemplateReturning(0, 1, 0, 0)
        );

        assertThatThrownBy(() -> guard.verifyNoLegacyRows(CONTEXT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reset the personal demo database or volume");
    }

    private static JdbcTemplate jdbcTemplateReturning(Integer... counts) {
        AtomicInteger callIndex = new AtomicInteger();
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                return requiredType.cast(counts[callIndex.getAndIncrement()]);
            }
        };
    }
}
