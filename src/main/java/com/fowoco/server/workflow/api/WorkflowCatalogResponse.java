package com.fowoco.server.workflow.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.workflow.domain.WorkflowCatalog;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkflowCatalogResponse(
        String bundleId,
        String bundleVersion,
        String bundleStatus,
        String sourceRepository,
        Instant generatedAt,
        List<WorkflowDefinitionResponse> workflows
) {
    static WorkflowCatalogResponse from(WorkflowCatalog catalog) {
        return new WorkflowCatalogResponse(
                catalog.bundleId(),
                catalog.bundleVersion(),
                catalog.bundleStatus(),
                catalog.sourceRepository(),
                catalog.generatedAt(),
                catalog.workflows().stream().map(WorkflowDefinitionResponse::from).toList()
        );
    }
}
