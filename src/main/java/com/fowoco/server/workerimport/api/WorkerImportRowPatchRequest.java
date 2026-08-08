package com.fowoco.server.workerimport.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerImportRowPatchRequest(
        @Min(2) int rowNumber,
        Boolean excluded,
        @NotNull Map<String, String> values
) {
}
