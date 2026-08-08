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
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkTenantBootstrap;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerLinkViewService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final WorkerLinkTenantBootstrap workerLinkTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final WorkerLinkRepository workerLinkRepository;
    private final TaskRepository taskRepository;
    private final DocumentRequestDraftRepository documentRequestDraftRepository;
    private final WorkerLinkHasher workerLinkHasher;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkViewService(
            WorkerLinkTenantBootstrap workerLinkTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerLinkRepository workerLinkRepository,
            TaskRepository taskRepository,
            DocumentRequestDraftRepository documentRequestDraftRepository,
            WorkerLinkHasher workerLinkHasher,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerLinkTenantBootstrap = workerLinkTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerLinkRepository = workerLinkRepository;
        this.taskRepository = taskRepository;
        this.documentRequestDraftRepository = documentRequestDraftRepository;
        this.workerLinkHasher = workerLinkHasher;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerLinkViewResult view(String rawToken, RequestMetadata metadata) {
        String tokenHash = workerLinkHasher.hash(rawToken);

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

        Task task = taskRepository.findByIdAndCompanyId(link.taskId(), companyId)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));
        DocumentRequestDraft draft = documentRequestDraftRepository
                .findByTaskIdAndCompanyId(link.taskId(), companyId)
                .filter(value -> value.message() != null && !value.message().isBlank())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_CONTENT_NOT_READY));

        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                companyId,
                ActorType.WORKER_LINK,
                null,
                null,
                AuditAction.WORKER_LINK_ACCESSED,
                AuditTargetType.TASK,
                link.taskId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "근로자 링크 안내 조회",
                now
        ));

        return new WorkerLinkViewResult(
                draft.message(),
                draft.language(),
                task.dueDate(),
                draft.documentTypes(),
                List.of(WorkerResponseType.values())
        );
    }
}
