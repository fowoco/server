package com.fowoco.server.workerlink.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.workerlink.application.WorkerLinkIssueCommand;
import com.fowoco.server.workerlink.application.WorkerLinkIssueResult;
import com.fowoco.server.workerlink.application.WorkerLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Link", description = "근로자 보안 링크 발급")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/worker-link")
@SecurityRequirement(name = "bearerAuth")
public class WorkerLinkController {

    private final WorkerLinkService workerLinkService;
    private final ActorContextProvider actorContextProvider;

    public WorkerLinkController(
            WorkerLinkService workerLinkService,
            ActorContextProvider actorContextProvider
    ) {
        this.workerLinkService = workerLinkService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "issueWorkerLink",
            summary = "근로자 보안 링크 발급",
            description = "승인된 현재 업무카드 version에서만 발급 가능합니다. "
                    + "rotate_existing=true면 기존 활성 링크를 즉시 폐기하고 재발급합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "발급 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerLinkIssueResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", description = "이미 유효한 링크가 있어 rotate_existing 필요"),
            @ApiResponse(responseCode = "422", description = "승인되지 않은 업무카드")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<WorkerLinkIssueResponse> issue(
            @Parameter(description = "업무 ID") @PathVariable UUID taskId,
            @Valid @RequestBody WorkerLinkIssueRequest request,
            //: 나중에 확인 필요
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        WorkerLinkIssueCommand command = new WorkerLinkIssueCommand(
                taskId,
                request.getExpiresInHours(),
                request.isRotateExisting(),
                idempotencyKey
        );
        WorkerLinkIssueResult result = workerLinkService.issue(command, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkerLinkIssueResponse.from(result));
    }
}
