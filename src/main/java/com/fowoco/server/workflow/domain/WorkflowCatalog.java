package com.fowoco.server.workflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record WorkflowCatalog(
        String bundleId,
        String bundleVersion,
        String bundleStatus,
        String sourceRepository,
        Instant generatedAt,
        List<WorkflowDefinition> workflows,
        List<WorkflowCaseTemplate> caseTemplates
) {

    public WorkflowCatalog {
        workflows = List.copyOf(workflows);
        caseTemplates = List.copyOf(caseTemplates);
    }

    public WorkflowCatalog(
            String bundleId,
            String bundleVersion,
            String bundleStatus,
            String sourceRepository,
            Instant generatedAt,
            List<WorkflowDefinition> workflows
    ) {
        this(
                bundleId,
                bundleVersion,
                bundleStatus,
                sourceRepository,
                generatedAt,
                workflows,
                List.of()
        );
    }

    public Optional<WorkflowDefinition> findWorkflow(String workflowId) {
        return workflows.stream()
                .filter(workflow -> workflow.workflowId().equals(workflowId))
                .findFirst();
    }

    public List<WorkflowDefinition> findByIntent(String intent) {
        return workflows.stream()
                .filter(workflow -> workflow.intent().equals(intent))
                .toList();
    }

    public List<WorkflowCaseTemplate> findCaseTemplatesByIntent(String intent) {
        return caseTemplates.stream()
                .filter(template -> template.intent().equals(intent))
                .toList();
    }
}
