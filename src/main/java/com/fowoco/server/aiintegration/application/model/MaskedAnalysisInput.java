package com.fowoco.server.aiintegration.application.model;

import java.util.List;
import java.util.Objects;

/**
 * Pseudonymized instruction and allow-listed context sent to the AI Runtime.
 */
public record MaskedAnalysisInput(
        String maskedInstruction,
        List<MaskedWorkerContext> workers,
        List<WorkflowConstraint> workflowConstraints
) {

    public MaskedAnalysisInput {
        Objects.requireNonNull(maskedInstruction, "maskedInstruction must not be null");
        Objects.requireNonNull(workers, "workers must not be null");
        Objects.requireNonNull(workflowConstraints, "workflowConstraints must not be null");
        workers = List.copyOf(workers);
        workflowConstraints = List.copyOf(workflowConstraints);
    }
}
