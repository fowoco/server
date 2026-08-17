package com.fowoco.server.worker.archive.api;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.port.ActorContextProvider;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.worker.archive.application.WorkerArchiveCommand;
import com.fowoco.server.worker.archive.application.WorkerArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Worker Archive", description = "퇴사 근로자의 안전 보관 가능 여부 확인·처리")
@RestController
@RequestMapping("/api/v1/workers/{workerId}")
@SecurityRequirement(name = "bearerAuth")
public class WorkerArchiveController {

    private final WorkerArchiveService archiveService;
    private final ActorContextProvider actorContextProvider;

    public WorkerArchiveController(
            WorkerArchiveService archiveService,
            ActorContextProvider actorContextProvider
    ) {
        this.archiveService = archiveService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(summary = "근로자 보관 가능 여부 확인")
    @GetMapping("/archive-eligibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerArchiveEligibilityResponse checkEligibility(@PathVariable UUID workerId) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        return WorkerArchiveEligibilityResponse.from(archiveService.checkEligibility(workerId, actor));
    }

    @Operation(summary = "퇴사 근로자 안전 보관")
    @PostMapping("/archive")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public WorkerArchiveResponse archive(
            @PathVariable UUID workerId,
            @Valid @RequestBody WorkerArchiveRequest request,
            HttpServletRequest servletRequest
    ) {
        ActorContext actor = actorContextProvider.requireCurrentActor();
        return WorkerArchiveResponse.from(archiveService.archive(
                new WorkerArchiveCommand(workerId, request.reason(), request.expectedVersion()),
                actor,
                RequestMetadata.from(servletRequest)
        ));
    }
}
