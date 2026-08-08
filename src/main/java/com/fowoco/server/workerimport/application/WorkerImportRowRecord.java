package com.fowoco.server.workerimport.application;

import com.fowoco.server.workerimport.domain.WorkerImportRowStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkerImportRowRecord(
        UUID importRowId,
        UUID importId,
        UUID companyId,
        int rowNumber,
        Map<String, String> sourceValues,
        Map<String, String> overrideValues,
        Map<String, String> normalizedValues,
        List<ImportValidationError> validationErrors,
        WorkerImportRowStatus status,
        UUID workerId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
