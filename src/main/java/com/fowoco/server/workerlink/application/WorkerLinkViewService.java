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
    private final WorkerLinkHasher workerLinkHasher;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkViewService(
            WorkerLinkTenantBootstrap workerLinkTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerLinkRepository workerLinkRepository,
            WorkerLinkHasher workerLinkHasher,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerLinkTenantBootstrap = workerLinkTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerLinkRepository = workerLinkRepository;
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

        // document-request-draft 연동해서 guidance/dueDate 실제 값 채우기.
        // AI Agent(Language Agent) 다음에 다시 확인
        return new WorkerLinkViewResult(
                "document-request-draft 연동 전",
                null,
                List.of(WorkerResponseType.values())
        );
    }
}
