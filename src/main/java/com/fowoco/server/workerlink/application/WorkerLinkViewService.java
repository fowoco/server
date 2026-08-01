package com.fowoco.server.workerlink.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.security.TenantDatabaseContext;
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

    private final WorkerLinkTenantBootstrap workerLinkTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final WorkerLinkRepository workerLinkRepository;
    private final WorkerLinkHasher workerLinkHasher;
    private final Clock clock;

    public WorkerLinkViewService(
            WorkerLinkTenantBootstrap workerLinkTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerLinkRepository workerLinkRepository,
            WorkerLinkHasher workerLinkHasher,
            Clock clock
    ) {
        this.workerLinkTenantBootstrap = workerLinkTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerLinkRepository = workerLinkRepository;
        this.workerLinkHasher = workerLinkHasher;
        this.clock = clock;
    }

    @Transactional
    public WorkerLinkViewResult view(String rawToken) {
        String tokenHash = workerLinkHasher.hash(rawToken);

        UUID companyId = workerLinkTenantBootstrap
                .findCompanyIdByWorkerLinkTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);

        WorkerLink link = workerLinkRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        Instant now = clock.instant();
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_EXPIRED);
        }

        // document-request-draft 연동해서 guidance/dueDate 실제 값 채우기.
        // AI Agent(Language Agent) 다음에 다시 확인 
        return new WorkerLinkViewResult(
                "document-request-draft 연동 전",
                null,
                List.of(WorkerResponseType.values())
        );
    }
}
