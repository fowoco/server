package com.fowoco.server.document.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.document.application.DocumentPageResult;
import com.fowoco.server.document.application.DocumentService;
import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Document", description = "사업장 통합 문서함 조회")
@RestController
@RequestMapping("/api/v1/documents")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class DocumentController {

    private final DocumentService documentService;
    private final ActorContextProvider actorContextProvider;

    public DocumentController(
            DocumentService documentService,
            ActorContextProvider actorContextProvider
    ) {
        this.documentService = documentService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "listDocuments",
            summary = "통합 문서함 조회",
            description = "근로자·업무·문서 유형·상태로 사업장 전체 서류를 검색합니다. "
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentPageResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public DocumentPageResponse list(
            @Parameter(description = "근로자 ID 필터") @RequestParam(required = false) UUID workerId,
            @Parameter(description = "업무 ID 필터") @RequestParam(required = false) UUID taskId,
            @Parameter(description = "서류 유형 필터") @RequestParam(required = false) DocumentType documentType,
            @Parameter(description = "제출 상태 필터") @RequestParam(required = false) SubmissionStatus status,
            @Parameter(description = "이 날짜 이전 만료 필터") @RequestParam(required = false) LocalDate expiryBefore,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "페이지당 항목 수 (1~100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        WorkerDocumentSearchQuery query = new WorkerDocumentSearchQuery(
                workerId,
                taskId,
                documentType,
                status,
                expiryBefore,
                page,
                size
        );
        DocumentPageResult result = documentService.findPage(actor, query);
        List<DocumentItemResponse> items = result.items().stream()
                .map(document -> DocumentItemResponse.from(
                        document,
                        result.workerDisplayNames().get(document.workerId())
                ))
                .toList();
        return new DocumentPageResponse(items, result.page(), result.size(), result.totalElements());
    }
}
