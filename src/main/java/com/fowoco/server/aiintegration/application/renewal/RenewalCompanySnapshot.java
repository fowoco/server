package com.fowoco.server.aiintegration.application.renewal;

import java.time.Instant;
import java.util.UUID;

public record RenewalCompanySnapshot(
        UUID companyId,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
