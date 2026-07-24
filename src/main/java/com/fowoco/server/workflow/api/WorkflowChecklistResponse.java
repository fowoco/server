package com.fowoco.server.workflow.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.workflow.domain.WorkflowChecklistTemplate;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkflowChecklistResponse(
        String itemCode,
        String label,
        boolean required
) {
    static WorkflowChecklistResponse from(WorkflowChecklistTemplate item) {
        return new WorkflowChecklistResponse(item.itemCode(), item.label(), item.required());
    }
}
