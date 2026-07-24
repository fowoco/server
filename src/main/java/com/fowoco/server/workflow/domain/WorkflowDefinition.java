package com.fowoco.server.workflow.domain;

import com.fowoco.server.task.domain.TaskType;
import java.util.List;
import java.util.Set;

public record WorkflowDefinition(
        String workflowId,
        String name,
        String intent,
        String sensitivity,
        Set<TaskType> supportedTaskTypes,
        Set<String> requiredSlots,
        List<WorkflowChecklistTemplate> checklistItems,
        List<String> completionEvidence,
        List<String> sourceIds
) {

    public WorkflowDefinition {
        supportedTaskTypes = Set.copyOf(supportedTaskTypes);
        requiredSlots = Set.copyOf(requiredSlots);
        checklistItems = List.copyOf(checklistItems);
        completionEvidence = List.copyOf(completionEvidence);
        sourceIds = List.copyOf(sourceIds);
    }
}
