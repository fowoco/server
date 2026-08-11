package com.fowoco.server.aiintegration.application.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HR instruction plus Server-managed phase context.
 *
 * <p>PLAN keeps context collections and the planned decision empty. ANALYZE preserves the PLAN
 * decision for persistence and validation, while the HTTP Adapter transmits its Intent and
 * Workflow IDs with requested field keys and resolved Worker values.</p>
 */
public record AnalysisInput(
        String instruction,
        Map<String, String> extractedSlots,
        List<String> requestedFieldKeys,
        List<WorkerContext> workers,
        List<WorkflowConstraint> workflowConstraints,
        AiIntentDecision plannedIntentDecision
) {

    public AnalysisInput(
            String instruction,
            Map<String, String> extractedSlots,
            List<String> requestedFieldKeys,
            List<WorkerContext> workers,
            List<WorkflowConstraint> workflowConstraints
    ) {
        this(instruction, extractedSlots, requestedFieldKeys, workers, workflowConstraints, null);
    }

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
