package com.fowoco.server.approval.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.approval.application.DecideApprovalCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RejectTaskRequest(
        @JsonProperty("expected_version") @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {

    public DecideApprovalCommand toCommand() {
        return new DecideApprovalCommand(expectedVersion, reason);
    }
}
