package com.fowoco.server.aiintegration.application.model;

import java.util.List;
import java.util.Objects;

/**
 * HR instruction plus phase-specific context sent to the AI Runtime.
 * PLAN keeps both collections empty; ANALYZE contains one Server-resolved Worker.
 */
public record AnalysisInput(
        String instruction,
        String intentHint,
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
