package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.RequestApprovalCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record ApprovalRequestBody(
        @PositiveOrZero long expectedVersion,
        boolean requirementsSatisfied,
        Map<String, Object> aiSnapshot,
        @NotNull Map<String, Object> hrSnapshot,
        @NotNull @Size(max = 100) List<@Size(max = 120) String> changedFields,
        @NotNull Map<String, Object> sourceVersions
) {

    public RequestApprovalCommand toCommand() {
        return new RequestApprovalCommand(
                expectedVersion,
                requirementsSatisfied,
                aiSnapshot,
                hrSnapshot,
                changedFields,
                sourceVersions
        );
    }
}
