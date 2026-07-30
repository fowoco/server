package com.fowoco.server.document.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.document.application.DocumentReadinessResult;
import com.fowoco.server.document.application.DocumentReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Document", description = "사업장 통합 문서함 조회")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/document-readiness")
@SecurityRequirement(name = "bearerAuth")
public class DocumentReadinessController {

    private final DocumentReadinessService documentReadinessService;
    private final ActorContextProvider actorContextProvider;

    public DocumentReadinessController(
            DocumentReadinessService documentReadinessService,
            ActorContextProvider actorContextProvider
    ) {
        this.documentReadinessService = documentReadinessService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "getDocumentReadiness",
            summary = "업무별 서류 준비도 확인",
            description = "Task 생성 시 고정된 체크리스트 snapshot을 기준으로 현재 업무에 필요한 "
                    + "서류가 준비됐는지 확인합니다. Workflow Catalog를 실시간으로 다시 읽지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentReadinessResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public DocumentReadinessResponse get(
            @Parameter(description = "업무 ID") @PathVariable UUID taskId
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        DocumentReadinessResult result = documentReadinessService.calculate(taskId, actor);
        return new DocumentReadinessResponse(
                result.required(),
                result.available(),
                result.missing(),
                result.expired(),
                result.completionBlocked()
        );
    }
}
