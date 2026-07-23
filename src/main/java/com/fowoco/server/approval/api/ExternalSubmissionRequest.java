package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.RecordExternalSubmissionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ExternalSubmissionRequest(
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 160) String destination,
        @NotBlank @Size(max = 300) String safeReference,
        @PastOrPresent Instant submittedAt
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
