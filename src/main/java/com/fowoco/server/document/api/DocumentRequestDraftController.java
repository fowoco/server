package com.fowoco.server.document.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.DocumentRequestDraftCommand;
import com.fowoco.server.document.application.DocumentRequestDraftService;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Document", description = "사업장 통합 문서함 조회")
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/document-request-draft")
@SecurityRequirement(name = "bearerAuth")
public class DocumentRequestDraftController {

    private final DocumentRequestDraftService documentRequestDraftService;
    private final ActorContextProvider actorContextProvider;

    public DocumentRequestDraftController(
            DocumentRequestDraftService documentRequestDraftService,
            ActorContextProvider actorContextProvider
    ) {
        this.documentRequestDraftService = documentRequestDraftService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "upsertDocumentRequestDraft",
            summary = "문서 요청 초안 저장",
            description = "누락 문서를 근로자에게 요청할 안내문 초안을 만들거나 갱신합니다. "
                    + "이 저장만으로는 Worker Link 생성이나 메시지 발송이 되지 않습니다. "
                    + "최초 생성 시 expected_version은 0이어야 합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "저장 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentRequestDraftResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public DocumentRequestDraftResponse upsert(
            @Parameter(description = "업무 ID") @PathVariable UUID taskId,
            @Valid @RequestBody DocumentRequestUpsertRequest request,
            HttpServletRequest servletRequest
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        UUID companyId = actor.companyId();
        DocumentRequestDraftCommand command = new DocumentRequestDraftCommand(
                taskId,
                companyId,
                request.getLanguage(),
                request.getDocumentTypes(),
                request.getMessage(),
                request.getExpectedVersion()
        );
        DocumentRequestDraft draft = documentRequestDraftService.upsert(
                command,
                actor,
                RequestMetadata.from(servletRequest)
        );
        return DocumentRequestDraftResponse.from(draft);
    }

    @Operation(
            operationId = "getDocumentRequestDraft",
            summary = "문서 요청 초안 조회",
            description = "저장된 요청 안내문과 최신 version을 조회하여 화면을 복구합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentRequestDraftResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public DocumentRequestDraftResponse find(
            @Parameter(description = "업무 ID") @PathVariable UUID taskId
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        return DocumentRequestDraftResponse.from(documentRequestDraftService.find(taskId, actor));
    }
}
