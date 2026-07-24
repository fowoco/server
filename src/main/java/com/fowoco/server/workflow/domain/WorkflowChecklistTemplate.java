package com.fowoco.server.workflow.domain;

public record WorkflowChecklistTemplate(
        String itemCode,
        String label,
        boolean required
) {
}
