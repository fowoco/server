package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.DecideApprovalCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RejectTaskRequest(
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {

    public DecideApprovalCommand toCommand() {
        return new DecideApprovalCommand(expectedVersion, reason);
    }
}
