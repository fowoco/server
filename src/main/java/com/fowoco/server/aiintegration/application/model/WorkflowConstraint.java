package com.fowoco.server.aiintegration.application.model;

import java.util.Objects;
import java.util.Set;

/**
 * Knowledge-owned Workflow identifiers and slot names that the Runtime may return.
 */
public record WorkflowConstraint(
        String workflowId,
        Set<String> allowedSlotKeys
) {

    public WorkflowConstraint {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(allowedSlotKeys, "allowedSlotKeys must not be null");
        allowedSlotKeys = Set.copyOf(allowedSlotKeys);
    }
}
