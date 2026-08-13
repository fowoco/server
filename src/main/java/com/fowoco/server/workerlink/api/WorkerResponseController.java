package com.fowoco.server.workerlink.api;

import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.WorkerResponseService;
import com.fowoco.server.workerlink.application.WorkerResponseSubmitCommand;
import com.fowoco.server.workerlink.application.WorkerResponseSubmitResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Link (Public)", description = "근로자 공개 안내·제출")
@RestController
public class WorkerResponseController {

    private final WorkerResponseService workerResponseService;

    public WorkerResponseController(WorkerResponseService workerResponseService) {
        this.workerResponseService = workerResponseService;
    }

    @Operation(
            operationId = "submitWorkerResponse",
            summary = "근로자 응답 제출",
            description = "같은 idempotency_key로 재시도해도 중복 응답이 생기지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "제출 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerResponseSubmitResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "410", description = "링크를 찾을 수 없거나 더 이상 사용할 수 없음"),
            @ApiResponse(responseCode = "413", description = "요청 크기 초과"),
            @ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
            @ApiResponse(responseCode = "422", description = "허용되지 않은 upload_id"),
            @ApiResponse(responseCode = "429", description = "요청 과다")
    })
    @PostMapping(
            path = "/api/v1/public/worker-links/{token}/responses",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<WorkerResponseSubmitResponse> submit(
            @Parameter(description = "근로자 링크 토큰") @PathVariable String token,
            @Valid @RequestBody WorkerResponseSubmitRequest request,
            HttpServletRequest servletRequest
    ) {
        WorkerResponseSubmitCommand command = new WorkerResponseSubmitCommand(
                token,
                request.getResponseType(),
                request.getMessage(),
                request.getUploadIds(),
                request.getAnswers(),
                request.getIdempotencyKey()
        );
        WorkerResponseSubmitResult result = workerResponseService.submit(
                command,
                RequestMetadata.from(servletRequest)
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkerResponseSubmitResponse.from(result));
    }
}
