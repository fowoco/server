package com.fowoco.server.workflow.domain;

import com.fowoco.server.task.domain.TaskType;
import java.util.List;
import java.util.Set;

public record WorkflowCaseTemplate(
        String caseTemplateId,
        String name,
        String intent,
        Set<String> workflowIds,
        List<TaskTemplate> tasks
) {

    public WorkflowCaseTemplate {
        workflowIds = Set.copyOf(workflowIds);
        tasks = List.copyOf(tasks);
    }

    public record TaskTemplate(
            String key,
            int order,
            TaskType taskType,
            String workflowId,
            String title,
            String description,
            Activation activation,
            List<String> dependsOn,
            List<String> dependsOnIfPresent,
            List<WorkflowChecklistTemplate> checklistItems,
            List<String> completionEvidence
    ) {

        public TaskTemplate {
            dependsOn = List.copyOf(dependsOn);
            dependsOnIfPresent = List.copyOf(dependsOnIfPresent);
            checklistItems = List.copyOf(checklistItems);
            completionEvidence = List.copyOf(completionEvidence);
        }
    }

    public record Activation(ActivationMode mode, Set<String> fieldKeys) {

        public Activation {
            fieldKeys = Set.copyOf(fieldKeys);
        }
    }

    public enum ActivationMode {
        ALWAYS,
        MISSING_ANY
    }
}
