package com.fowoco.server.workerlink.api;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.WorkerLinkDocumentService;
import com.fowoco.server.workerlink.application.WorkerLinkDocumentUploadCommand;
import com.fowoco.server.workerlink.application.WorkerLinkDocumentUploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Worker Link (Public)", description = "근로자 공개 안내·제출")
@RestController
public class WorkerLinkDocumentController {

    private final WorkerLinkDocumentService workerLinkDocumentService;

    public WorkerLinkDocumentController(WorkerLinkDocumentService workerLinkDocumentService) {
        this.workerLinkDocumentService = workerLinkDocumentService;
    }

    @Operation(
            operationId = "uploadWorkerLinkDocument",
            summary = "근로자 링크 문서 제출",
            description = "만료·폐기된 token은 거절합니다. 확장자·MIME·크기·악성파일 검사 후 격리 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "업로드 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerLinkDocumentUploadResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "410", description = "링크를 찾을 수 없거나 더 이상 사용할 수 없음"),
            @ApiResponse(responseCode = "413", description = "파일 크기 초과"),
            @ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity"),
            @ApiResponse(responseCode = "429", description = "요청 과다")
    })
    @PostMapping(
            path = "/api/v1/public/worker-links/{token}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<WorkerLinkDocumentUploadResponse> upload(
            @Parameter(description = "근로자 링크 토큰") @PathVariable String token,
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "문서 유형") @RequestParam(value = "documentType", required = false) String documentType,
            @Parameter(description = "클라이언트 중복 방지 키") @RequestParam("clientRequestId") String clientRequestId,
            HttpServletRequest servletRequest
    ) {
        if (file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "업로드할 파일과 파일명이 필요합니다.");
        }
        try {
            WorkerLinkDocumentUploadCommand command = new WorkerLinkDocumentUploadCommand(
                    token,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    documentType,
                    clientRequestId,
                    file.getInputStream()
            );
            WorkerLinkDocumentUploadResult result = workerLinkDocumentService.upload(
                    command,
                    RequestMetadata.from(servletRequest)
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(WorkerLinkDocumentUploadResponse.from(result.storedFile(), result.linkExpiresAt()));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded file", exception);
        }
    }
}
