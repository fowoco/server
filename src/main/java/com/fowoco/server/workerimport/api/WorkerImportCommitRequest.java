package com.fowoco.server.workerimport.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record WorkerImportCommitRequest(
        @NotNull Long expectedVersion,
        Set<@Min(2) Integer> selectedRowNumbers
) {
    public WorkerImportCommitRequest {
        selectedRowNumbers = selectedRowNumbers == null ? Set.of() : Set.copyOf(selectedRowNumbers);
    }
}
