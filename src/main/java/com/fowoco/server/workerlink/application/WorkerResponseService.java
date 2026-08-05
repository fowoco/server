package com.fowoco.server.workerlink.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkTenantBootstrap;
import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.application.port.WorkerResponseUploadAlreadyLinkedException;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerResponse;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerResponseService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final WorkerLinkTenantBootstrap workerLinkTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final WorkerLinkRepository workerLinkRepository;
    private final WorkerResponseRepository workerResponseRepository;
    private final WorkerLinkHasher workerLinkHasher;
    private final StoredFileRepository storedFileRepository;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerResponseService(
            WorkerLinkTenantBootstrap workerLinkTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerLinkRepository workerLinkRepository,
            WorkerResponseRepository workerResponseRepository,
            WorkerLinkHasher workerLinkHasher,
            StoredFileRepository storedFileRepository,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerLinkTenantBootstrap = workerLinkTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerLinkRepository = workerLinkRepository;
        this.workerResponseRepository = workerResponseRepository;
        this.workerLinkHasher = workerLinkHasher;
        this.storedFileRepository = storedFileRepository;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerResponseSubmitResult submit(WorkerResponseSubmitCommand command, RequestMetadata metadata) {
        String tokenHash = workerLinkHasher.hash(command.rawToken());

        UUID companyId = workerLinkTenantBootstrap
                .findCompanyIdByWorkerLinkTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);

        WorkerLink link = workerLinkRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        Instant now = clock.instant();
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND);
        }

        Optional<WorkerResponse> existing = workerResponseRepository
                .findByWorkerLinkIdAndIdempotencyKey(link.workerLinkId(), command.idempotencyKey());
        if (existing.isPresent()) {
            WorkerResponse previous = existing.get();
            return new WorkerResponseSubmitResult(previous.responseId(), previous.receivedAt());
        }

        List<UUID> uploadIds = command.uploadIds() != null ? command.uploadIds() : List.of();
        for (UUID uploadId : uploadIds) {
            StoredFile storedFile = storedFileRepository.findByIdAndCompanyId(uploadId, companyId)
                    .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE));
            if (!storedFile.verified() || !link.taskId().equals(storedFile.taskId())) {
                throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
            }
            if (workerResponseRepository.isUploadAlreadyLinked(uploadId, companyId)) {
                throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
            }
        }

        UUID responseId = uuidGenerator.generate();
        WorkerResponse response = WorkerResponse.create(
                responseId,
                link.workerLinkId(),
                companyId,
                command.responseType(),
                command.message(),
                command.idempotencyKey(),
                now
        );
        workerResponseRepository.insert(response);

        for (UUID uploadId : uploadIds) {
            try {
                workerResponseRepository.linkUpload(responseId, uploadId, companyId);
            } catch (WorkerResponseUploadAlreadyLinkedException exception) {
                throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
            }
        }

        if (command.responseType() == WorkerResponseType.QUESTION
                || command.responseType() == WorkerResponseType.NOT_UNDERSTOOD) {
            workerLinkRepository.update(link.markNeedsFollowup(now));
            // HR 후속 업무/활동 이력
        }

        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                companyId,
                ActorType.WORKER_LINK,
                null,
                null,
                AuditAction.WORKER_LINK_RESPONSE_SUBMITTED,
                AuditTargetType.TASK,
                link.taskId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "근로자 응답 제출: " + command.responseType(),
                now
        ));

        return new WorkerResponseSubmitResult(responseId, now);
    }
}
