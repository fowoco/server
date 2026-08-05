package com.fowoco.server.airun.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAiRunRequest(
        @NotBlank
        @Size(max = 10_000)
        String instruction
) {
}
