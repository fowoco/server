package com.fowoco.server.settings.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.settings.application.CompanyMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Company Members", description = "같은 사업장의 선택 가능한 구성원 조회")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/company-members")
public class CompanyMemberController {

    private final CompanyMemberService companyMemberService;
    private final ActorContextProvider actorContextProvider;

    public CompanyMemberController(
            CompanyMemberService companyMemberService,
            ActorContextProvider actorContextProvider
    ) {
        this.companyMemberService = companyMemberService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "listCompanyMembers",
            summary = "사업장 구성원 목록 조회",
            description = "같은 사업장의 구성원을 이름과 ID 순으로 반환합니다. "
                    + "VIEWER는 필터 사용과 역할·상태·승인 권한 조회가 제한됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "역할별 구성원 projection 조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CompanyMemberListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "adminOrHr",
                                            value = """
                                                    {"items":[{
                                                      "user_id":"7e2722bb-3c72-4aa0-b37c-28931c4f8e53",
                                                      "display_name":"김인사",
                                                      "roles":["HR"],
                                                      "active":true,
                                                      "approval_permission":true
                                                    }]}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "viewer",
                                            value = """
                                                    {"items":[{
                                                      "user_id":"7e2722bb-3c72-4aa0-b37c-28931c4f8e53",
                                                      "display_name":"김인사"
                                                    }]}
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CompanyMemberListResponse findAll(
            @Parameter(description = "ADMIN | HR | VIEWER. ADMIN/HR만 사용 가능")
            @RequestParam(required = false) UserRole role,
            @Parameter(description = "derived approval_permission 필터. ADMIN/HR만 사용 가능")
            @RequestParam(name = "approval_capable", required = false) Boolean approvalCapable,
            @Parameter(description = "true면 ACTIVE만, false면 상태 필터 없음")
            @RequestParam(name = "active_only", defaultValue = "true") boolean activeOnly
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        return CompanyMemberListResponse.from(companyMemberService.findAll(
                actor,
                role,
                approvalCapable,
                activeOnly
        ));
    }
}
