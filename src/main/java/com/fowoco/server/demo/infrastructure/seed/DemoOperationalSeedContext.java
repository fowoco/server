package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

record DemoOperationalSeedContext(
        UUID companyId,
        UUID actorId,
        LocalDate today,
        Instant now
) {

    DemoOperationalSeedContext {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(today, "today must not be null");
        Objects.requireNonNull(now, "now must not be null");
    }

    static DemoOperationalSeedContext from(DemoAuthSeedProperties properties, Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        return new DemoOperationalSeedContext(
                properties.companyId(),
                properties.adminUserId(),
                today,
                now
        );
    }
}
