package com.fowoco.server.aiintegration.application.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HR instruction plus Server-managed phase context.
 *
 * <p>PLAN keeps context collections empty. ANALYZE preserves the context needed for validation,
 * while the HTTP Adapter transmits only requested field keys and resolved Worker values.</p>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
