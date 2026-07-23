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
        List<WorkflowDefinition> workflows
) {

    public WorkflowCatalog {
        workflows = List.copyOf(workflows);
    }

    public Optional<WorkflowDefinition> findWorkflow(String workflowId) {
        return workflows.stream()
                .filter(workflow -> workflow.workflowId().equals(workflowId))
                .findFirst();
    }
}
