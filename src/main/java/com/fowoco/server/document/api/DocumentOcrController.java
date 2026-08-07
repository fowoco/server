package com.fowoco.server.document.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.DocumentOcrReviewCommand;
import com.fowoco.server.document.application.DocumentOcrRunResult;
import com.fowoco.server.document.application.DocumentOcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Document OCR", description = "HR 전용 문서 OCR 실행·조회·검토")
@SecurityRequirement(name = "bearerAuth")
@RestController
@Validated
@RequestMapping("/api/v1/documents/{documentId}/ocr-runs")
public class DocumentOcrController {

    private final DocumentOcrService documentOcrService;
    private final ActorContextProvider actorContextProvider;

    public DocumentOcrController(
            DocumentOcrService documentOcrService,
            ActorContextProvider actorContextProvider
    ) {
        this.documentOcrService = documentOcrService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "createDocumentOcrRun",
            summary = "연결된 파일의 OCR 실행 요청",
            description = "요청을 QUEUED 상태로 저장한 뒤 AI Runtime을 비동기로 호출합니다. "
                    + "추출값은 암호화해 저장하며 근로자 정보에는 자동 반영하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "OCR 실행 접수"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "422", description = "OCR 불가 서류·파일·국가"),
            @ApiResponse(responseCode = "503", description = "OCR 비활성화")
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<DocumentOcrRunResponse> create(
            @Parameter(description = "근로자 서류 ID") @PathVariable UUID documentId,
            @Parameter(description = "동일 요청의 중복 실행 방지 키", required = true)
            @RequestHeader("Idempotency-Key")
            @Size(min = 8, max = 100, message = "Idempotency-Key는 8자 이상 100자 이하여야 합니다.")
            String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        DocumentOcrRunResult result = documentOcrService.create(
                documentId,
                idempotencyKey,
                actor,
                RequestMetadata.from(servletRequest)
        );
        URI location = URI.create("/api/v1/documents/" + documentId + "/ocr-runs/" + result.run().ocrRunId());
        return ResponseEntity.accepted().location(location).body(DocumentOcrRunResponse.from(result));
    }

    @Operation(operationId = "getDocumentOcrRun", summary = "OCR 실행 상태·결과 조회")
    @GetMapping(path = "/{ocrRunId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public DocumentOcrRunResponse findById(
            @PathVariable UUID documentId,
            @PathVariable UUID ocrRunId,
            HttpServletRequest servletRequest
    ) {
        return DocumentOcrRunResponse.from(documentOcrService.findById(
                documentId,
                ocrRunId,
                actorContextProvider.requireCurrentActor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    @Operation(operationId = "getLatestDocumentOcrRun", summary = "문서의 최신 OCR 실행 조회")
    @GetMapping(path = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public DocumentOcrRunResponse findLatest(
            @PathVariable UUID documentId,
            HttpServletRequest servletRequest
    ) {
        return DocumentOcrRunResponse.from(documentOcrService.findLatest(
                documentId,
                actorContextProvider.requireCurrentActor(),
                RequestMetadata.from(servletRequest)
        ));
    }

    @Operation(
            operationId = "reviewDocumentOcrRun",
            summary = "OCR 결과 승인·반려",
            description = "OCR 결과를 검토 상태로만 확정합니다. Worker·민감정보 테이블은 자동 수정하지 않습니다."
    )
    @PostMapping(
            path = "/{ocrRunId}/review",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public DocumentOcrRunResponse review(
            @PathVariable UUID documentId,
            @PathVariable UUID ocrRunId,
            @Valid @RequestBody DocumentOcrReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return DocumentOcrRunResponse.from(documentOcrService.review(
                documentId,
                ocrRunId,
                new DocumentOcrReviewCommand(
                        request.expectedVersion(),
                        request.decision(),
                        request.reason()
                ),
                actorContextProvider.requireCurrentActor(),
                RequestMetadata.from(servletRequest)
        ));
    }
}
