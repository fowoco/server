package com.fowoco.server.approval.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.approval.application.RecordExternalSubmissionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ExternalSubmissionRequest(
        @JsonProperty("expected_version") @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 160) String destination,
        @JsonProperty("safe_reference") @NotBlank @Size(max = 300) String safeReference,
        @JsonProperty("submitted_at") @PastOrPresent Instant submittedAt
) {

    public RecordExternalSubmissionCommand toCommand() {
        return new RecordExternalSubmissionCommand(
                expectedVersion,
                destination,
                safeReference,
                submittedAt
        );
    }
}
