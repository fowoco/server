package com.fowoco.server.workerlink.api;

import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.WorkerLinkViewResult;
import com.fowoco.server.workerlink.application.WorkerLinkViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Link (Public)", description = "근로자 공개 안내·제출")
@RestController
@RequestMapping("/api/v1/public/worker-links/{token}")
public class WorkerLinkViewController {

    private final WorkerLinkViewService workerLinkViewService;

    public WorkerLinkViewController(WorkerLinkViewService workerLinkViewService) {
        this.workerLinkViewService = workerLinkViewService;
    }

    @Operation(
            operationId = "viewWorkerLink",
            summary = "근로자 공개 안내 조회",
            description = "로그인 없이 번역된 최소 안내와 허용 응답을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerLinkViewResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "409", description = "근로자 안내 초안이 아직 준비되지 않음"),
            @ApiResponse(responseCode = "410", description = "링크를 찾을 수 없거나 더 이상 사용할 수 없음"),
            @ApiResponse(responseCode = "429", description = "요청 과다")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkerLinkViewResponse> view(
            @Parameter(description = "근로자 링크 토큰") @PathVariable String token,
            HttpServletRequest servletRequest
    ) {
        WorkerLinkViewResult result = workerLinkViewService.view(token, RequestMetadata.from(servletRequest));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(WorkerLinkViewResponse.from(result));
    }
}
