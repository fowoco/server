package com.fowoco.server.reliability.api;

import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.reliability.application.OutboxManualRetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Operations", description = "ADMIN 전용 장애 복구 작업")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/outbox-events")
public class OutboxOperationsController {

    private final OutboxManualRetryService retryService;
    private final ActorContextProvider actorContextProvider;

    public OutboxOperationsController(
            OutboxManualRetryService retryService,
            ActorContextProvider actorContextProvider
    ) {
        this.retryService = retryService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "retryOutboxEvent",
            summary = "REVIEW_REQUIRED Outbox 이벤트 재처리",
            description = "ADMIN이 원인을 해결한 뒤 멈춘 이벤트를 PENDING으로 되돌립니다. "
                    + "payload·예외 원문은 반환하지 않으며 실제 처리는 Outbox worker가 수행합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "재처리 요청 접수"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(
            path = "/{eventId}/retry",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<OutboxManualRetryResponse> retry(
            @Parameter(description = "재처리할 Outbox event ID")
            @PathVariable UUID eventId,
            @Parameter(description = "같은 운영 요청의 중복 실행을 막는 키", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OutboxManualRetryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.accepted().body(OutboxManualRetryResponse.from(
                retryService.requestRetry(
                        eventId,
                        request.expectedVersion(),
                        request.reason(),
                        idempotencyKey,
                        actorContextProvider.requireCurrentActor(),
                        RequestMetadata.from(servletRequest)
                )
        ));
    }
}
