package com.fowoco.server.common.database;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DatabaseTimeoutMetrics {

    private final Counter statementTimeouts;
    private final Counter lockTimeouts;

    public DatabaseTimeoutMetrics(MeterRegistry meterRegistry) {
        statementTimeouts = timeoutCounter(meterRegistry, "statement");
        lockTimeouts = timeoutCounter(meterRegistry, "lock");
    }

    public void recordConfirmed(DatabaseTimeoutType type) {
        switch (type) {
            case CONFIRMED_STATEMENT_TIMEOUT -> statementTimeouts.increment();
            case CONFIRMED_LOCK_TIMEOUT -> lockTimeouts.increment();
            default -> throw new IllegalArgumentException(
                    "Only confirmed database timeouts can be recorded"
            );
        }
    }

    private Counter timeoutCounter(MeterRegistry registry, String type) {
        return Counter.builder("fowoco.database.timeouts")
                .description("Confirmed PostgreSQL Runtime Connection timeouts")
                .tag("type", type)
                .register(registry);
    }
}
