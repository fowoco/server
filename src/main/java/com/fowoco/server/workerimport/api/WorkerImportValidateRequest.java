package com.fowoco.server.workerimport.api;

import jakarta.validation.constraints.NotNull;

public record WorkerImportValidateRequest(@NotNull Long expectedVersion) {
}
