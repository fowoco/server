package com.fowoco.server.workerimport.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WorkerImportRowsRequest(
        @NotNull Long expectedVersion,
        @NotEmpty List<@Valid WorkerImportRowPatchRequest> rows
) {
}
