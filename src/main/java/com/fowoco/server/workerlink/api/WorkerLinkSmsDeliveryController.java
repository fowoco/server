package com.fowoco.server.workerlink.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.WorkerLinkDeliveryResult;
import com.fowoco.server.workerlink.application.WorkerLinkSmsDeliveryCommand;
import com.fowoco.server.workerlink.application.WorkerLinkSmsDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Link", description = "근로자 보안 링크 발급과 전달")
@Validated
@RestController
@SecurityRequirement(name = "bearerAuth")
public class WorkerLinkSmsDeliveryController {

    private final WorkerLinkSmsDeliveryService service;
    private final ActorContextProvider actorContextProvider;

    public WorkerLinkSmsDeliveryController(
            WorkerLinkSmsDeliveryService service,
            ActorContextProvider actorContextProvider
    ) {
        this.service = service;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "sendWorkerLinkSms",
            summary = "근로자 보안 링크 SMS 발송",
            description = "발급 요청에 사용한 Idempotency-Key와 원본 token이 현재 링크와 일치할 때만 "
                    + "SMS를 발송합니다. 실제 Provider 성공 후에만 SENT로 기록합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발송 성공 또는 이미 발송된 기존 결과 반환"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity"),
            @ApiResponse(responseCode = "502", description = "SMS Provider 발송 실패"),
            @ApiResponse(responseCode = "503", description = "SMS Provider 비활성")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            path = "/api/v1/worker-links/{workerLinkId}/sms-deliveries",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public WorkerLinkDeliveryResponse send(
            @Parameter(description = "근로자 링크 ID") @PathVariable UUID workerLinkId,
            @NotBlank @Size(max = 200)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WorkerLinkSmsDeliveryRequest request,
            HttpServletRequest servletRequest
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        WorkerLinkDeliveryResult result = service.deliver(
                new WorkerLinkSmsDeliveryCommand(
                        workerLinkId,
                        request.getRecipientPhone(),
                        request.getWorkerLinkToken(),
                        idempotencyKey
                ),
                actor,
                RequestMetadata.from(servletRequest)
        );
        return WorkerLinkDeliveryResponse.from(result);
    }
}
