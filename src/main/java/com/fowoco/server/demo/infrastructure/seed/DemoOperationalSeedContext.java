package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
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

    static DemoOperationalSeedContext demo(
            DemoAuthSeedProperties properties,
            LocalDate today,
            Instant now
    ) {
        Objects.requireNonNull(properties, "properties must not be null");
        return new DemoOperationalSeedContext(
                properties.companyId(),
                properties.adminUserId(),
                today,
                now
        );
    }

    static DemoOperationalSeedContext test(
            DemoAuthSeedProperties properties,
            LocalDate today,
            Instant now
    ) {
        Objects.requireNonNull(properties, "properties must not be null");
        return new DemoOperationalSeedContext(
                properties.testCompanyId(),
                DemoOperationalSeedCatalog.TEST_ADMIN_USER_ID,
                today,
                now
        );
    }
}
