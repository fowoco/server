package com.fowoco.server.dashboard.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.dashboard.application.DashboardQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.LocalDate;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "오늘 업무 대시보드 요약")
@RestController
@RequestMapping("/api/v1/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final ActorContextProvider actorContextProvider;

    public DashboardController(
            DashboardQueryService dashboardQueryService,
            ActorContextProvider actorContextProvider
    ) {
        this.dashboardQueryService = dashboardQueryService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "getDashboardToday",
            summary = "오늘 대시보드 조회",
            description = "오늘 우선 업무·승인 대기·응답 대기·기한 경고를 한 번에 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DashboardTodayResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @GetMapping(path = "/today", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public DashboardTodayResponse today(
            @Parameter(description = "기준 날짜 (생략 시 서버 오늘 날짜)") @RequestParam(required = false) LocalDate date,
            @Parameter(description = "IANA 타임존 ID (예: Asia/Seoul, 생략 시 서버 기본 타임존)")
            @RequestParam(required = false) String timezone
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        return dashboardQueryService.today(actor, date, timezone);
    }
}
