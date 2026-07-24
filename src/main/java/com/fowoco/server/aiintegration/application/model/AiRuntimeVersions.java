package com.fowoco.server.aiintegration.application.model;

import java.util.Objects;

/**
 * Version evidence persisted with a future AiRun.
 */
public record AiRuntimeVersions(
        String agentVersion,
        String modelProvider,
        String modelName,
        String modelVersion,
        String promptVersion,
        String contextPackVersion,
        String workflowCatalogVersion,
        String contractVersion
) {

    public AiRuntimeVersions {
        Objects.requireNonNull(agentVersion, "agentVersion must not be null");
        Objects.requireNonNull(modelProvider, "modelProvider must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        Objects.requireNonNull(promptVersion, "promptVersion must not be null");
        Objects.requireNonNull(contextPackVersion, "contextPackVersion must not be null");
        Objects.requireNonNull(workflowCatalogVersion, "workflowCatalogVersion must not be null");
        Objects.requireNonNull(contractVersion, "contractVersion must not be null");
    }
}
