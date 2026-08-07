package com.fowoco.server.workerimport.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerImportMappingRequest(
        @NotNull Long expectedVersion,
        @NotEmpty Map<String, String> mappings
) {
}
