package com.fowoco.server.casework.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.casework.application.CaseQueryService;
import com.fowoco.server.casework.application.CaseSearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Case", description = "업무함 Case와 Workflow 진행 현황")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseQueryService caseQueryService;
    private final ActorContextProvider actorContextProvider;

    public CaseController(
            CaseQueryService caseQueryService,
            ActorContextProvider actorContextProvider
    ) {
        this.caseQueryService = caseQueryService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(operationId = "listCases", summary = "업무함 Case 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사업장 범위 Case 목록"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CasePageResponse findAll(
            @Parameter(description = "근로자 표시 이름 또는 Case 제목 검색")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지당 Case 수 (1~100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return CasePageResponse.from(caseQueryService.findPage(
                new CaseSearchQuery(keyword, page, size),
                actor()
        ));
    }

    @Operation(operationId = "getCaseProjection", summary = "Case 진행 현황 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Case와 하위 업무 진행 현황"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    @GetMapping(path = "/{caseId}/projection", produces = MediaType.APPLICATION_JSON_VALUE)
    public CaseProjectionResponse findProjection(@PathVariable UUID caseId) {
        return CaseProjectionResponse.from(caseQueryService.findById(caseId, actor()));
    }

    private ActorContext actor() {
        return actorContextProvider.requireCurrentActor();
    }
}
