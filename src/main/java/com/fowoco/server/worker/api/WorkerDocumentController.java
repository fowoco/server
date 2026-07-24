package com.fowoco.server.worker.api;

import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.worker.application.WorkerDocumentCreateCommand;
import com.fowoco.server.worker.application.WorkerDocumentPatchCommand;
import com.fowoco.server.worker.application.WorkerDocumentService;
import com.fowoco.server.worker.domain.WorkerDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Document", description = "근로자 서류 상태 등록·수정")
@RestController
@RequestMapping("/api/v1/workers/{workerId}/documents")
@SecurityRequirement(name = "bearerAuth")
public class WorkerDocumentController {

    private final WorkerDocumentService workerDocumentService;
    private final ActorContextProvider actorContextProvider;

    public WorkerDocumentController(
            WorkerDocumentService workerDocumentService,
            ActorContextProvider actorContextProvider
    ) {
        this.workerDocumentService = workerDocumentService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "registerWorkerDocument",
            summary = "서류 상태 등록",
            description = "근로자 서류의 제출·유효기간·제출처 상태를 등록합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerDocumentResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<WorkerDocumentResponse> register(
            @Parameter(description = "근로자 ID") @PathVariable UUID workerId,
            @Valid @RequestBody WorkerDocumentCreateRequest request
    ) {
        UUID companyId = actorContextProvider.requireCurrentActor().companyId();
        WorkerDocumentCreateCommand command = new WorkerDocumentCreateCommand(
                workerId,
                companyId,
                request.getDocumentType(),
                request.getSubmissionStatus(),
                request.getExpiryDate(),
                request.getDestination(),
                request.getNote()
        );
        WorkerDocument document = workerDocumentService.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkerDocumentResponse.from(document));
    }

    @Operation(
            operationId = "patchWorkerDocument",
            summary = "서류 상태 수정",
            description = "서류 제출·검증·만료 상태와 유효기간을 수정합니다. "
                    + "expected_version이 현재 값과 다르면 409로 응답합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerDocumentResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(
                    responseCode = "409",
                    description = "expected_version 충돌. OpenApiConfig에 공통 Conflict 응답이 아직 없어 "
                            + "인라인으로 정의함",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = com.fowoco.server.common.error.ApiErrorResponse.class)
                    )
            )
    })
    @PatchMapping(
            path = "/{documentId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerDocumentResponse patch(
            @Parameter(description = "근로자 ID") @PathVariable UUID workerId,
            @Parameter(description = "서류 ID") @PathVariable UUID documentId,
            @Valid @RequestBody WorkerDocumentPatchRequest request
    ) {
        UUID companyId = actorContextProvider.requireCurrentActor().companyId();
        WorkerDocumentPatchCommand command = new WorkerDocumentPatchCommand(
                documentId,
                workerId,
                companyId,
                request.getDocumentType(),
                request.getSubmissionStatus(),
                request.getExpiryDate(),
                request.getDestination(),
                request.getNote(),
                request.getExpectedVersion()
        );
        WorkerDocument document = workerDocumentService.patch(command);
        return WorkerDocumentResponse.from(document);
    }
}
