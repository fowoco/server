package com.fowoco.server.workerimport.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.workerimport.application.ImportValidationError;
import com.fowoco.server.workerimport.application.WorkerImportRowRecord;
import com.fowoco.server.workerimport.domain.WorkerImportRowStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerImportRowResponse(
        int rowNumber,
        Map<String, String> sourceValues,
        Map<String, String> overrideValues,
        Map<String, String> normalizedValues,
        WorkerImportRowStatus status,
        List<ImportValidationError> errors,
        UUID workerId,
        long version
) {
    static WorkerImportRowResponse from(WorkerImportRowRecord row) {
        return new WorkerImportRowResponse(
                row.rowNumber(), row.sourceValues(), row.overrideValues(), row.normalizedValues(),
                row.status(), row.validationErrors(), row.workerId(), row.version()
        );
    }
}
