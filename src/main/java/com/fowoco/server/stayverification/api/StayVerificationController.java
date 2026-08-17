package com.fowoco.server.stayverification.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.stayverification.application.StayVerificationCommand;
import com.fowoco.server.stayverification.application.StayVerificationService;
import com.fowoco.server.stayverification.domain.StayVerificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stay Verification", description = "체류기간 만료 경과 대상의 긴급 확인 Case")
@RestController
@RequestMapping("/api/v1/stay-verifications")
@SecurityRequirement(name = "bearerAuth")
public class StayVerificationController {

    private final StayVerificationService service;
    private final ActorContextProvider actorContextProvider;

    public StayVerificationController(
            StayVerificationService service,
            ActorContextProvider actorContextProvider
    ) {
        this.service = service;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(summary = "체류기간 경과 대상 즉시 스캔", description = "일일 배치와 같은 멱등 규칙으로 현재 사업장만 스캔합니다.")
    @PostMapping(path = "/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public Map<String, Integer> scan(HttpServletRequest servletRequest) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        int created = service.scanCompany(actor, RequestMetadata.from(servletRequest));
        return Map.of("created_count", created);
    }

    @Operation(summary = "체류상태 확인 Case 목록 조회")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public List<StayVerificationResponse> list(
            @RequestParam(required = false) StayVerificationStatus status
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        return service.findAll(status, actor).stream()
                .map(StayVerificationResponse::from)
                .toList();
    }

    @Operation(summary = "체류상태 확인 결과와 증빙 기록")
    @PatchMapping(
            path = "/{stayVerificationId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public StayVerificationResponse update(
            @PathVariable UUID stayVerificationId,
            @Valid @RequestBody StayVerificationUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        StayVerificationCommand command = new StayVerificationCommand(
                stayVerificationId,
                request.status(),
                request.extensionAppliedAt(),
                request.extensionReceiptDocumentId(),
                request.approvalResultDocumentId(),
                request.newStayExpiryDate(),
                request.officialConsultationNote(),
                request.employmentEndConfirmedAt(),
                request.recheckDate(),
                request.expectedVersion()
        );
        return StayVerificationResponse.from(
                service.update(command, actor, RequestMetadata.from(servletRequest))
        );
    }
}
