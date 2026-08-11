package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.renewal.RenewalExecutionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RenewalExecutionRequest(
        @NotBlank @Size(max = 10_000) String instruction,
        @NotNull @Min(0) Long expectedVersion,
        @Schema(
                description = "직전 Renewal 응답에서 source_hint가 USER_INPUT인 Slot의 HR 답변",
                example = "{\"wage\":\"2500000\",\"working_hours\":\"40\"}"
        )
        @Size(max = 50) Map<String, String> slotAnswers
) {
    RenewalExecutionCommand toCommand() {
        Map<String, String> answers = slotAnswers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(slotAnswers));
        return new RenewalExecutionCommand(instruction, expectedVersion, answers);
    }
}
