package com.fowoco.server.workerimport.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerimport.application.WorkerImportRowPatch;
import com.fowoco.server.workerimport.application.WorkerImportService;
import com.fowoco.server.workerimport.application.error.WorkerImportErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Worker Import", description = "CSV/XLSX 근로자 명단 검토·등록")
@RestController
@RequestMapping("/api/v1/imports")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class WorkerImportController {

    private final WorkerImportService service;
    private final ActorContextProvider actorContextProvider;

    public WorkerImportController(WorkerImportService service, ActorContextProvider actorContextProvider) {
        this.service = service;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(summary = "근로자 명단 가져오기 생성", description = "CSV/XLSX를 파싱해 검토 작업을 만듭니다. 아직 근로자는 등록하지 않습니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<WorkerImportResponse> create(
            @RequestPart("file") MultipartFile file,
            @RequestHeader("Idempotency-Key")
            @Size(min = 8, max = 100) String idempotencyKey,
            HttpServletRequest request
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        try {
            var result = service.create(
                    file.getOriginalFilename(), file.getBytes(), idempotencyKey,
                    actor, RequestMetadata.from(request)
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(WorkerImportResponse.from(result));
        } catch (IOException exception) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
        }
    }

    @Operation(summary = "가져오기 작업 조회", description = "행별 검증 결과와 등록 진행 상황을 조회합니다.")
    @GetMapping(path = "/{importId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerImportResponse find(
            @PathVariable UUID importId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size
    ) {
        return WorkerImportResponse.from(service.find(
                importId, page, size, actorContextProvider.requireCurrentActor()
        ));
    }

    @Operation(summary = "가져오기 열 연결 저장", description = "업로드 파일의 열을 Worker 필드에 연결합니다.")
    @PutMapping(path = "/{importId}/mappings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerImportResponse saveMappings(
            @PathVariable UUID importId,
            @Valid @RequestBody WorkerImportMappingRequest body,
            HttpServletRequest request
    ) {
        return WorkerImportResponse.from(service.saveMappings(
                importId, body.expectedVersion(), body.mappings(),
                actorContextProvider.requireCurrentActor(), RequestMetadata.from(request)
        ));
    }

    @Operation(summary = "가져오기 행 검증", description = "연결된 값을 날짜·필수값·중복 후보 규칙으로 검증합니다.")
    @PostMapping(path = "/{importId}/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerImportResponse validate(
            @PathVariable UUID importId,
            @Valid @RequestBody WorkerImportValidateRequest body,
            HttpServletRequest request
    ) {
        return WorkerImportResponse.from(service.validate(
                importId, body.expectedVersion(), actorContextProvider.requireCurrentActor(),
                RequestMetadata.from(request), false
        ));
    }

    @Operation(summary = "가져오기 오류 행 수정·제외", description = "시스템 필드 값을 고치거나 등록 대상에서 제외합니다.")
    @PatchMapping(path = "/{importId}/rows", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerImportResponse patchRows(
            @PathVariable UUID importId,
            @Valid @RequestBody WorkerImportRowsRequest body,
            HttpServletRequest request
    ) {
        var patches = body.rows().stream()
                .map(row -> new WorkerImportRowPatch(row.rowNumber(), row.excluded(), row.values()))
                .toList();
        return WorkerImportResponse.from(service.patchRows(
                importId, body.expectedVersion(), patches, actorContextProvider.requireCurrentActor(),
                RequestMetadata.from(request)
        ));
    }

    @Operation(summary = "정상 행 등록 확정", description = "선택한 VALID 행만 Worker로 등록하며 재호출로 중복 생성하지 않습니다.")
    @PostMapping(path = "/{importId}/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerImportResponse commit(
            @PathVariable UUID importId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) String idempotencyKey,
            @Valid @RequestBody WorkerImportCommitRequest body,
            HttpServletRequest request
    ) {
        return WorkerImportResponse.from(service.commit(
                importId, body.expectedVersion(), body.selectedRowNumbers(), idempotencyKey,
                actorContextProvider.requireCurrentActor(), RequestMetadata.from(request)
        ));
    }

    @Operation(summary = "오류 행 재검증", description = "수정된 행을 포함해 아직 등록하지 않은 행을 다시 검증합니다.")
    @PostMapping(path = "/{importId}/retry", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerImportResponse retry(
            @PathVariable UUID importId,
            @Valid @RequestBody WorkerImportValidateRequest body,
            HttpServletRequest request
    ) {
        return WorkerImportResponse.from(service.validate(
                importId, body.expectedVersion(), actorContextProvider.requireCurrentActor(),
                RequestMetadata.from(request), true
        ));
    }
}
