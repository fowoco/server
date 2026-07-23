package com.fowoco.server.approval.api;

import com.fowoco.server.approval.application.ApprovalResult;
import com.fowoco.server.approval.application.ApprovalService;
import com.fowoco.server.approval.application.TaskActionResult;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Approval", description = "업무 승인·반려·제출·증빙·완료")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}")
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ActorContextProvider actorContextProvider;

    public ApprovalController(
            ApprovalService approvalService,
            ActorContextProvider actorContextProvider
    ) {
        this.approvalService = approvalService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "requestTaskApproval",
            summary = "업무 승인 요청",
            description = "필수정보를 확인하고 AI 원본·HR 최종본·출처 버전을 snapshot으로 고정합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "승인 요청 생성"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", description = "버전 충돌 또는 진행 중 승인 존재"),
            @ApiResponse(responseCode = "422", description = "상태·필수정보·민감정보 규칙 위반")
    })
    @PostMapping(
            path = "/approval-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApprovalResponse> requestApproval(
            @PathVariable UUID taskId,
            @Valid @RequestBody ApprovalRequestBody request,
            HttpServletRequest servletRequest
    ) {
        ApprovalResult result = approvalService.requestApproval(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(201)
                .body(ApprovalResponse.from(result));
    }

    @Operation(operationId = "approveTask", summary = "업무 승인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "현재 Task version 승인"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", description = "Task 또는 승인 대상 version 충돌"),
            @ApiResponse(responseCode = "422", description = "허용되지 않은 상태")
    })
    @PostMapping(
            path = "/approve",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ApprovalResponse approve(
            @PathVariable UUID taskId,
            @Valid @RequestBody ApproveTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApprovalResponse.from(approvalService.approve(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    @Operation(
            operationId = "rejectTask",
            summary = "업무 반려",
            description = "승인 요청을 종료하고 Task를 DRAFT로 되돌립니다."
    )
    @PostMapping(
            path = "/reject",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ApprovalResponse reject(
            @PathVariable UUID taskId,
            @Valid @RequestBody RejectTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApprovalResponse.from(approvalService.reject(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    @Operation(
            operationId = "recordExternalSubmission",
            summary = "외부기관 제출 기록",
            description = "서버가 기관에 대신 제출하지 않고 HR이 수행한 제출 결과만 기록합니다."
    )
    @PostMapping(
            path = "/external-submissions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TaskActionResponse> recordExternalSubmission(
            @PathVariable UUID taskId,
            @Valid @RequestBody ExternalSubmissionRequest request,
            HttpServletRequest servletRequest
    ) {
        TaskActionResult result = approvalService.recordExternalSubmission(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(201)
                .body(TaskActionResponse.from(result));
    }

    @Operation(operationId = "recordTaskEvidence", summary = "업무 증빙 기록")
    @PostMapping(
            path = "/evidence",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TaskActionResponse> recordEvidence(
            @PathVariable UUID taskId,
            @Valid @RequestBody EvidenceRequest request,
            HttpServletRequest servletRequest
    ) {
        TaskActionResult result = approvalService.recordEvidence(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(201)
                .body(TaskActionResponse.from(result));
    }

    @Operation(operationId = "completeTask", summary = "업무 완료")
    @PostMapping(
            path = "/complete",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public TaskActionResponse complete(
            @PathVariable UUID taskId,
            @Valid @RequestBody CompleteTaskRequest request,
            HttpServletRequest servletRequest
    ) {
        return TaskActionResponse.from(approvalService.complete(
                taskId,
                request.toCommand(),
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    private ActorContext actor() {
        return actorContextProvider.requireCurrentActor();
    }
}
