package com.fowoco.server.workerimport.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerImportCommitRequest(
        @NotNull Long expectedVersion,
        Set<@Min(2) Integer> selectedRowNumbers
) {
    public WorkerImportCommitRequest {
        selectedRowNumbers = selectedRowNumbers == null ? Set.of() : Set.copyOf(selectedRowNumbers);
    }
}
