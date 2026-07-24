package com.fowoco.server.approval.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.approval.application.RequestApprovalCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record ApprovalRequestBody(
        @JsonProperty("expected_version") @PositiveOrZero long expectedVersion,
        @JsonProperty("ai_snapshot") Map<String, Object> aiSnapshot,
        @JsonProperty("hr_snapshot") @NotNull Map<String, Object> hrSnapshot,
        @JsonProperty("changed_fields")
        @NotNull @Size(max = 100) List<@Size(max = 120) String> changedFields,
        @JsonProperty("source_versions") @NotNull Map<String, Object> sourceVersions
) {

    public RequestApprovalCommand toCommand() {
        return new RequestApprovalCommand(
                expectedVersion,
                aiSnapshot,
                hrSnapshot,
                changedFields,
                sourceVersions
        );
    }
}
