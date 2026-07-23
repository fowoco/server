package com.fowoco.server.worker.api;

import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.worker.application.WorkerCreateCommand;
import com.fowoco.server.worker.application.WorkerPatchCommand;
import com.fowoco.server.worker.application.WorkerService;
import com.fowoco.server.worker.domain.Worker;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker", description = "근로자 기본정보 등록·조회·수정")
@RestController
@RequestMapping("/api/v1/workers")
@SecurityRequirement(name = "bearerAuth")
public class WorkerController {

    private final WorkerService workerService;
    private final ActorContextProvider actorContextProvider;

    public WorkerController(
            WorkerService workerService,
            ActorContextProvider actorContextProvider
    ) {
        this.workerService = workerService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(
            operationId = "registerWorker",
            summary = "근로자 등록",
            description = "업무에 필요한 최소 개인정보로 근로자를 등록합니다. "
                    + "여권번호·외국인등록번호·전화번호·계좌번호는 이 API로 수집하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "등록 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<WorkerResponse> register(@Valid @RequestBody WorkerCreateRequest request) {
        UUID companyId = actorContextProvider.requireCurrentActor().companyId();
        WorkerCreateCommand command = new WorkerCreateCommand(
                companyId,
                request.getDisplayName(),
                request.getNationalityCode(),
                request.getPreferredLanguage(),
                request.getVisaExpiryDate(),
                request.getContractStartDate(),
                request.getContractEndDate()
        );
        Worker worker = workerService.register(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkerResponse.from(worker));
    }

    @Operation(
            operationId = "getWorkerDetail",
            summary = "근로자 상세 조회",
            description = "근로자 기본정보와 체류·계약기간, 서류 상태 요약을 조회합니다. "
                    + "타 사업장 소속 근로자 ID는 404로 응답합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @GetMapping(path = "/{workerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public WorkerResponse getDetail(
            @Parameter(description = "근로자 ID") @PathVariable UUID workerId
    ) {
        UUID companyId = actorContextProvider.requireCurrentActor().companyId();
        Worker worker = workerService.findDetail(workerId, companyId);
        return WorkerResponse.from(worker);
    }

    @Operation(
            operationId = "patchWorker",
            summary = "근로자 수정",
            description = "근무상태·언어·체류일·계약기간을 부분 수정합니다. "
                    + "expected_version이 현재 값과 다르면 409로 응답합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = WorkerResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(
                    responseCode = "409",
                    description = "expected_version이 현재 값과 달라 다른 사용자의 수정과 충돌함. "
                            + "OpenApiConfig에 공통 Conflict 응답이 아직 없어 인라인으로 정의함 — 팀장님 확인 필요",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = com.fowoco.server.common.error.ApiErrorResponse.class)
                    )
            )
    })
    @PatchMapping(
            path = "/{workerId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerResponse patch(
            @Parameter(description = "근로자 ID") @PathVariable UUID workerId,
            @Valid @RequestBody WorkerPatchRequest request
    ) {
        UUID companyId = actorContextProvider.requireCurrentActor().companyId();
        WorkerPatchCommand command = new WorkerPatchCommand(
                workerId,
                companyId,
                request.getDisplayName(),
                request.getNationalityCode(),
                request.getPreferredLanguage(),
                request.getWorkStatus(),
                request.getVisaExpiryDate(),
                request.getContractStartDate(),
                request.getContractEndDate(),
                request.getExpectedVersion()
        );
        Worker worker = workerService.patch(command);
        return WorkerResponse.from(worker);
    }
}
