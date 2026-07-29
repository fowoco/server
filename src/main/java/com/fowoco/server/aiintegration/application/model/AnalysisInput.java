package com.fowoco.server.aiintegration.application.model;

import java.util.List;
import java.util.Objects;

/**
 * Original HR instruction and Worker context sent to the AI Runtime for the current demo.
 */
public record AnalysisInput(
        String instruction,
        List<WorkerContext> workers,
        List<WorkflowConstraint> workflowConstraints
) {

    public AnalysisInput {
        Objects.requireNonNull(instruction, "instruction must not be null");
        Objects.requireNonNull(workers, "workers must not be null");
        Objects.requireNonNull(workflowConstraints, "workflowConstraints must not be null");
        workers = List.copyOf(workers);
        workflowConstraints = List.copyOf(workflowConstraints);
    }
}
