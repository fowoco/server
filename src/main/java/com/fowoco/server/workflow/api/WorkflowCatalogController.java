package com.fowoco.server.workflow.api;

import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.workflow.application.WorkflowCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Workflow Catalog", description = "Knowledge release에서 투영한 업무 정의 조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/workflow-catalogs")
@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
public class WorkflowCatalogController {

    private final WorkflowCatalogService catalogService;
    private final ActorContextProvider actorContextProvider;
    private final ActorAuthorizer actorAuthorizer;

    public WorkflowCatalogController(
            WorkflowCatalogService catalogService,
            ActorContextProvider actorContextProvider,
            ActorAuthorizer actorAuthorizer
    ) {
        this.catalogService = catalogService;
        this.actorContextProvider = actorContextProvider;
        this.actorAuthorizer = actorAuthorizer;
    }

    @Operation(
            operationId = "getActiveWorkflowCatalog",
            summary = "활성 Workflow Catalog 조회",
            description = "Server가 검증하고 고정한 Knowledge projection과 source version을 반환합니다."
    )
    @GetMapping
    public WorkflowCatalogResponse getActiveCatalog() {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        return WorkflowCatalogResponse.from(catalogService.getActiveCatalog());
    }
}
