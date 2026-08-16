package com.fowoco.server.file.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.FileCreateCommand;
import com.fowoco.server.file.application.FileDownloadResult;
import com.fowoco.server.file.application.FilePreviewResult;
import com.fowoco.server.file.application.FilePreviewService;
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
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "File", description = "공통 파일 업로드·다운로드")
@RestController
@RequestMapping("/api/v1/files")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileService fileService;
    private final FilePreviewService filePreviewService;
    private final ActorContextProvider actorContextProvider;

    public FileController(
            FileService fileService,
            FilePreviewService filePreviewService,
            ActorContextProvider actorContextProvider
    ) {
        this.fileService = fileService;
        this.filePreviewService = filePreviewService;
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
            @Parameter(description = "연결할 근로자 ID") @RequestParam(value = "workerId", required = false) UUID workerId,
            HttpServletRequest servletRequest
    ) {
        if (file.isEmpty() || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "업로드할 파일과 파일명이 필요합니다.");
        }

        ActorContext actor = actorContextProvider.requireCurrentActor();
        UUID companyId = actor.companyId();
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
            StoredFile storedFile = fileService.upload(command, actor, RequestMetadata.from(servletRequest));
            return ResponseEntity.status(HttpStatus.CREATED).body(FileUploadResponse.from(storedFile));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded file", exception);
        }
    }

    @Operation(
            operationId = "downloadFile",
            summary = "파일 다운로드",
            description = "현재 로그인한 사용자의 사업장에 속한 파일만 다운로드합니다. "
                    + "다른 사업장의 파일은 존재 여부가 드러나지 않도록 404로 응답합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "다운로드 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @GetMapping("/{fileId}/content")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public ResponseEntity<InputStreamResource> download(
            @Parameter(description = "다운로드할 파일 ID") @PathVariable UUID fileId,
            HttpServletRequest servletRequest
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        FileDownloadResult result = fileService.download(fileId, actor, RequestMetadata.from(servletRequest));
        MediaType mediaType = parseMediaType(result.storedFile().mimeType());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(result.storedFile().name(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(result.storedFile().size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(result.content()));
    }

    @Operation(
            operationId = "previewFile",
            summary = "파일 미리보기",
            description = "PDF와 이미지는 브라우저에서 바로 표시하고 HWP·HWPX는 AI 문서 변환기를 통해 PDF로 반환합니다. "
                    + "원본 파일은 변경하지 않으며 다른 사업장의 파일은 404로 응답합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "미리보기 성공. HWP·HWPX는 application/pdf로 반환",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
            @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity"),
            @ApiResponse(responseCode = "503", description = "HWP·HWPX 변환 서비스를 사용할 수 없음")
    })
    @GetMapping("/{fileId}/preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public ResponseEntity<InputStreamResource> preview(
            @Parameter(description = "미리보기할 파일 ID") @PathVariable UUID fileId,
            HttpServletRequest servletRequest
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        FilePreviewResult result = filePreviewService.preview(
                fileId,
                actor,
                RequestMetadata.from(servletRequest)
        );
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(result.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(parseMediaType(result.mimeType()))
                .contentLength(result.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(result.content()));
    }

    private MediaType parseMediaType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
