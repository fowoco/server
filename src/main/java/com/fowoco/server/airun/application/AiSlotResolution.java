package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.WorkerContext;
import com.fowoco.server.aiintegration.application.model.WorkflowConstraint;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tenant-scoped values and Knowledge constraints prepared for an ANALYZE request.
 */
public record AiSlotResolution(
        WorkerContext worker,
        List<WorkflowConstraint> workflowConstraints,
        Map<String, String> resolvedFields,
        Set<String> missingFieldKeys
) {

    public AiSlotResolution {
        Objects.requireNonNull(worker, "worker must not be null");
        Objects.requireNonNull(workflowConstraints, "workflowConstraints must not be null");
        Objects.requireNonNull(resolvedFields, "resolvedFields must not be null");
        Objects.requireNonNull(missingFieldKeys, "missingFieldKeys must not be null");
        workflowConstraints = List.copyOf(workflowConstraints);
        resolvedFields = Map.copyOf(resolvedFields);
        missingFieldKeys = Set.copyOf(missingFieldKeys);
    }
}
