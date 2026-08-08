package com.fowoco.server.workerlink.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.WorkerLinkDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Link", description = "근로자 보안 링크 발급과 전달 상태")
@Validated
@RestController
@SecurityRequirement(name = "bearerAuth")
public class WorkerLinkDeliveryController {

    private final WorkerLinkDeliveryService service;
    private final ActorContextProvider actorContextProvider;

    public WorkerLinkDeliveryController(
            WorkerLinkDeliveryService service,
            ActorContextProvider actorContextProvider
    ) {
        this.service = service;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "getTaskWorkerLinkDelivery",
            summary = "현재 근로자 링크 전달 상태 조회",
            description = "원본 token을 노출하지 않고 현재 활성 링크의 발급·전달 상태를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @GetMapping(
            path = "/api/v1/tasks/{taskId}/worker-link",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WorkerLinkDeliveryResponse findCurrent(
            @Parameter(description = "업무 ID") @PathVariable UUID taskId
    ) {
        return WorkerLinkDeliveryResponse.from(service.findCurrent(taskId, actor()));
    }

    @Operation(
            operationId = "markWorkerLinkSent",
            summary = "근로자 링크 전달 완료 기록",
            description = "HR이 외부 채널로 링크를 전달했다고 기록합니다. 실제 수신 확인을 의미하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전달 완료 기록 성공 또는 기존 결과 반환"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", description = "만료되거나 폐기된 링크")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            path = "/api/v1/worker-links/{workerLinkId}/sent",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WorkerLinkDeliveryResponse markSent(
            @Parameter(description = "근로자 링크 ID") @PathVariable UUID workerLinkId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        return WorkerLinkDeliveryResponse.from(service.markSent(
                workerLinkId,
                actor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    private ActorContext actor() {
        return actorContextProvider.requireCurrentActor();
    }
}
