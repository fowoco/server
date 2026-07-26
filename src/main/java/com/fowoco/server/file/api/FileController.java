package com.fowoco.server.file.api;

import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.file.application.FileCreateCommand;
import com.fowoco.server.file.application.FileService;
import com.fowoco.server.file.domain.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "File", description = "공통 파일 업로드")
@RestController
@RequestMapping("/api/v1/files")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileService fileService;
    private final ActorContextProvider actorContextProvider;

    public FileController(FileService fileService, ActorContextProvider actorContextProvider) {
        this.fileService = fileService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "uploadFile",
            summary = "파일 업로드",
            description = "분석·증빙·근로자 제출에 사용할 파일을 안전하게 저장하고 fileId를 발급합니다. "
                    + "악성파일 검사 인프라는 아직 없어 scan_status는 항상 NOT_SCANNED로 반환합니다. "
                    + "허용 크기·형식은 TODO — 확정 기준 없어 상식적인 기본값(20MB, image/jpeg·png·webp, application/pdf) 사용 중."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "업로드 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = FileUploadResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(
                    responseCode = "413",
                    description = "파일 크기 초과. OpenApiConfig에 공통 413 응답이 아직 없어 인라인으로 정의함"
            ),
            @ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<FileUploadResponse> upload(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "파일 용도") @RequestParam("purpose") String purpose,
            @Parameter(description = "연결할 업무 ID") @RequestParam(value = "taskId", required = false) UUID taskId,
            @Parameter(description = "연결할 근로자 ID") @RequestParam(value = "workerId", required = false) UUID workerId
    ) {
        if (file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "업로드할 파일과 파일명이 필요합니다.");
        }

        UUID companyId = actorContextProvider.requireCurrentActor().companyId();
        try {
            FileCreateCommand command = new FileCreateCommand(
                    companyId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    purpose,
                    taskId,
                    workerId,
                    file.getInputStream()
            );
            StoredFile storedFile = fileService.upload(command);
            return ResponseEntity.status(HttpStatus.CREATED).body(FileUploadResponse.from(storedFile));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded file", exception);
        }
    }
}
