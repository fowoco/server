package com.fowoco.server.aiintegration.application.renewal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record RenewalTaskSnapshot(
        UUID taskId,
        UUID companyId,
        UUID workerId,
        UUID caseId,
        String taskType,
        String workflowId,
        String workflowCatalogVersion,
        String title,
        String description,
        Map<String, Object> businessDataJson,
        long contentRevision,
        String source,
        String status,
        LocalDate dueDate,
        UUID createdBy,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public RenewalTaskSnapshot {
        businessDataJson = businessDataJson == null ? Map.of() : Map.copyOf(businessDataJson);
    }
}
