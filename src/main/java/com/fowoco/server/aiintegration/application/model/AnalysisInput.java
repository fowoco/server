package com.fowoco.server.aiintegration.application.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HR instruction plus phase-specific context sent to the AI Runtime.
 * PLAN keeps context collections empty; ANALYZE preserves extracted and requested fields and
 * contains one Server-resolved Worker.
 */
public record AnalysisInput(
        String instruction,
        Map<String, String> extractedSlots,
        List<String> requestedFieldKeys,
        List<WorkerContext> workers,
        List<WorkflowConstraint> workflowConstraints
) {

    public AnalysisInput {
        Objects.requireNonNull(instruction, "instruction must not be null");
        Objects.requireNonNull(extractedSlots, "extractedSlots must not be null");
        Objects.requireNonNull(requestedFieldKeys, "requestedFieldKeys must not be null");
        Objects.requireNonNull(workers, "workers must not be null");
        Objects.requireNonNull(workflowConstraints, "workflowConstraints must not be null");
        extractedSlots = Map.copyOf(extractedSlots);
        requestedFieldKeys = List.copyOf(requestedFieldKeys);
        workers = List.copyOf(workers);
        workflowConstraints = List.copyOf(workflowConstraints);
    }
}
