package com.fowoco.server.common.database;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DatabaseAccessDeniedMetrics {

    private final Counter accessDenied;

    public DatabaseAccessDeniedMetrics(MeterRegistry meterRegistry) {
        accessDenied = Counter.builder("fowoco.database.access.denied")
                .description("Confirmed PostgreSQL database access denials")
                .register(meterRegistry);
    }

    public void recordConfirmed() {
        accessDenied.increment();
    }
}
