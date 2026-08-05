package com.fowoco.server.airun.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SubmitAiRunAnswersRequest(
        @Min(0)
        long expectedVersion,
        @NotNull
        @NotEmpty
        @Size(max = 50)
        Map<String, String> answers
) {
}
