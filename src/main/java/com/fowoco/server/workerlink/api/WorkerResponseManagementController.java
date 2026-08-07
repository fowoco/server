package com.fowoco.server.workerlink.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.WorkerResponseManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Response", description = "HR용 근로자 응답 조회와 확인 처리")
@Validated
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/worker-responses")
@SecurityRequirement(name = "bearerAuth")
public class WorkerResponseManagementController {

    private final WorkerResponseManagementService service;
    private final ActorContextProvider actorContextProvider;

    public WorkerResponseManagementController(
            WorkerResponseManagementService service,
            ActorContextProvider actorContextProvider
    ) {
        this.service = service;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "getTaskWorkerResponses",
            summary = "업무의 근로자 응답 조회",
            description = "보안 링크로 접수된 질문·이해 어려움·파일 제출 응답을 최신순으로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkerResponsePageResponse findAll(
            @PathVariable UUID taskId,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지당 응답 수 (1~100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return WorkerResponsePageResponse.from(service.findAll(taskId, page, size, actor()));
    }

    @Operation(
            operationId = "markTaskWorkerResponsesReviewed",
            summary = "근로자 응답 확인 처리",
            description = "이 업무에 도착한 미확인 응답 묶음을 HR이 확인한 상태로 바꿉니다. 반복 호출해도 안전합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 처리 성공"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(path = "/read")
    public ResponseEntity<Void> markReviewed(
            @PathVariable UUID taskId,
            HttpServletRequest servletRequest
    ) {
        service.markReviewed(taskId, actor(), RequestMetadata.from(servletRequest));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private ActorContext actor() {
        return actorContextProvider.requireCurrentActor();
    }
}
