package com.fowoco.server.workerimport.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record WorkerImportRowPatchRequest(
        @Min(2) int rowNumber,
        Boolean excluded,
        @NotNull Map<String, String> values
) {
}
