package com.fowoco.server.workerimport.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerImportRowsRequest(
        @NotNull Long expectedVersion,
        @NotEmpty List<@Valid WorkerImportRowPatchRequest> rows
) {
}
