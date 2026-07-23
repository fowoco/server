package com.fowoco.server.audit.api;

import com.fowoco.server.audit.application.AuditQueryService;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit", description = "업무 활동 이력과 사업장 감사 조회")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditQueryService auditQueryService;
    private final ActorContextProvider actorContextProvider;

    public AuditController(
            AuditQueryService auditQueryService,
            ActorContextProvider actorContextProvider
    ) {
        this.auditQueryService = auditQueryService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "getTaskActivities",
            summary = "업무 활동 타임라인",
            description = "내부 감사 기록에서 화면에 안전하게 표시할 필드만 시간순으로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업무 활동 목록"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @GetMapping(
            path = "/tasks/{taskId}/activities",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public List<AuditEventResponse> taskActivities(@PathVariable UUID taskId) {
        return auditQueryService.getTaskActivities(
                        taskId,
                        actorContextProvider.requireCurrentActor()
                ).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    @Operation(
            operationId = "searchAuditEvents",
            summary = "사업장 감사 이벤트 검색",
            description = "ADMIN만 자신의 사업장 범위에서 필터와 불투명 cursor로 검색할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "감사 이벤트 페이지"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @GetMapping(path = "/audit-events", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditPageResponse search(
            @RequestParam(required = false) ActorType actorType,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false)
            @Pattern(regexp = "^[0-9a-f]{32}$") String traceId,
            @RequestParam(name = "created_from", required = false) Instant createdFrom,
            @RequestParam(name = "created_to", required = false) Instant createdTo,
            @Parameter(description = "직전 응답의 next_cursor. 내용을 해석하거나 수정하지 않습니다.")
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return AuditPageResponse.from(auditQueryService.search(
                actorType,
                action,
                targetType,
                targetId,
                traceId,
                createdFrom,
                createdTo,
                cursor,
                limit,
                actorContextProvider.requireCurrentActor()
        ));
    }
}
