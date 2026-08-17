package com.fowoco.server.worker.archive.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record WorkerArchiveRequest(
        @NotBlank @Size(max = 500) String reason,
        @JsonProperty("expected_version") @NotNull @PositiveOrZero Long expectedVersion
) {
}
