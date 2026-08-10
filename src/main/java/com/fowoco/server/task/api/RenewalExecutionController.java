package com.fowoco.server.task.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.task.application.renewal.RenewalExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Renewal Agent", description = "재계약·취업활동기간·체류기간 연장 Agent 실행")
@SecurityRequirement(name = "bearerAuth")
public class RenewalExecutionController {

    private final RenewalExecutionService executionService;
    private final ActorContextProvider actorContextProvider;

    public RenewalExecutionController(
            RenewalExecutionService executionService,
            ActorContextProvider actorContextProvider
    ) {
        this.executionService = executionService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            summary = "Renewal Agent 실행",
            description = "Task·Worker·Company·승인된 OCR 문맥을 Agent에 전달하고, 결과를 기존 Task와 안내 초안에 반영합니다. 자동 승인·발송·완료는 하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent 결과 검증·반영 완료"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "ADMIN·HR 권한 필요"),
            @ApiResponse(responseCode = "404", description = "업무카드 없음 또는 다른 사업장"),
            @ApiResponse(responseCode = "409", description = "업무카드 버전 충돌"),
            @ApiResponse(responseCode = "422", description = "Renewal 대상이 아니거나 요청 계약 오류"),
            @ApiResponse(responseCode = "503", description = "AI Runtime 일시 사용 불가")
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @PostMapping(
            path = "/{taskId}/renewal-run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public RenewalExecutionResponse execute(
            @PathVariable UUID taskId,
            @Valid @RequestBody RenewalExecutionRequest request,
            HttpServletRequest servletRequest
    ) {
        return RenewalExecutionResponse.from(executionService.execute(
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
