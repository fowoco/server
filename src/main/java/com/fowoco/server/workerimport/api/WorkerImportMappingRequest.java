package com.fowoco.server.workerimport.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WorkerImportMappingRequest(
        @NotNull Long expectedVersion,
        @NotEmpty Map<String, String> mappings
) {
}
