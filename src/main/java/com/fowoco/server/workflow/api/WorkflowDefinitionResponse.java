package com.fowoco.server.workflow.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import com.fowoco.server.task.domain.TaskType;
import java.util.List;
import java.util.Set;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkflowDefinitionResponse(
        String workflowId,
        String name,
        String intent,
        String sensitivity,
        Set<TaskType> supportedTaskTypes,
        Set<String> requiredSlots,
        List<WorkflowChecklistResponse> checklistItems,
        List<String> completionEvidence,
        List<String> sourceIds
) {
    static WorkflowDefinitionResponse from(WorkflowDefinition workflow) {
        return new WorkflowDefinitionResponse(
                workflow.workflowId(),
                workflow.name(),
                workflow.intent(),
                workflow.sensitivity(),
                workflow.supportedTaskTypes(),
                workflow.requiredSlots(),
                workflow.checklistItems().stream()
                        .map(WorkflowChecklistResponse::from)
                        .toList(),
                workflow.completionEvidence(),
                workflow.sourceIds()
        );
    }
}
