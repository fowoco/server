package com.fowoco.server.settings.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.settings.application.CompanySettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Company Settings", description = "사업장 공통 운영 정책 조회·수정")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/settings")
public class CompanySettingsController {

    private final CompanySettingsService companySettingsService;
    private final ActorContextProvider actorContextProvider;

    public CompanySettingsController(
            CompanySettingsService companySettingsService,
            ActorContextProvider actorContextProvider
    ) {
        this.companySettingsService = companySettingsService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "getCompanySettings",
            summary = "회사 설정 조회",
            description = "ADMIN, HR, VIEWER에게 Secret과 개인정보가 없는 동일한 public 설정 DTO를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "persisted 사업장 설정 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CompanySettingsResponse.class),
                            examples = @ExampleObject(
                                    name = "settings",
                                    value = """
                                            {
                                              "approval_policy": "ADMIN_OR_HR",
                                              "link_expiry_hours": 72,
                                              "evidence_rules": {
                                                "RECONTRACT": ["DOCUMENT"],
                                                "STAY_PERIOD_EXTENSION": ["OFFICIAL_RESULT"]
                                              },
                                              "file_retention_days": 365,
                                              "ai_log_retention_days": 90,
                                              "audit_visibility": "ADMIN_ONLY",
                                              "version": 0
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CompanySettingsResponse get() {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        return CompanySettingsResponse.from(companySettingsService.get(actor));
    }
}
