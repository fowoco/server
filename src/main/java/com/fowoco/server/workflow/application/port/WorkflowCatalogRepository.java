package com.fowoco.server.workflow.application.port;

import com.fowoco.server.workflow.domain.WorkflowCatalog;

public interface WorkflowCatalogRepository {

    WorkflowCatalog getActiveCatalog();
}
