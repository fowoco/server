package com.fowoco.server.aiintegration.application.renewal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RenewalWorkerSnapshot(
        UUID workerId,
        UUID companyId,
        String displayName,
        String nationalityCode,
        String preferredLanguage,
        String workStatus,
        String visaType,
        LocalDate stayExpiryDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        LocalDate employmentPermitEndDate,
        LocalDate employmentActivityEndDate,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
