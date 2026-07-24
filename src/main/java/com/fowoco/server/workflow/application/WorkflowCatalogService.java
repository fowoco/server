package com.fowoco.server.workflow.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.workflow.application.port.WorkflowCatalogRepository;
import com.fowoco.server.workflow.domain.WorkflowCatalog;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import org.springframework.stereotype.Service;

@Service
public class WorkflowCatalogService {

    private final WorkflowCatalogRepository catalogRepository;

    public WorkflowCatalogService(WorkflowCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    public WorkflowCatalog getActiveCatalog() {
        return catalogRepository.getActiveCatalog();
    }

    public WorkflowDefinition requireWorkflow(String workflowId) {
        return getActiveCatalog().findWorkflow(workflowId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.WORKFLOW_NOT_FOUND));
    }
}
