package com.fowoco.server.reliability.infrastructure.persistence;

import com.fowoco.server.reliability.application.port.OutboxTimeSource;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "transaction-only",
        matchIfMissing = true
)
public class ClockOutboxTimeSource implements OutboxTimeSource {

    private final Clock clock;

    public ClockOutboxTimeSource(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
