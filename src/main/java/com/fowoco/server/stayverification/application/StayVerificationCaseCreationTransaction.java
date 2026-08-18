package com.fowoco.server.stayverification.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository.ExpiredWorker;
import com.fowoco.server.stayverification.domain.StayVerificationCase;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StayVerificationCaseCreationTransaction {

    private static final String AUDIT_VERSION = "1";
    private final TenantDatabaseContext tenantDatabaseContext;
    private final StayVerificationRepository repository;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;

    public StayVerificationCaseCreationTransaction(
            TenantDatabaseContext tenantDatabaseContext,
            StayVerificationRepository repository,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator
    ) {
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean createIfAbsent(
            ExpiredWorker worker,
            Instant now,
            ActorType actorType,
            UUID actorId,
            UserRole role,
            String requestId,
            String traceId
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(worker.companyId());
        UUID verificationId = uuidGenerator.generate();
        if (!repository.insertIfAbsent(verificationId, worker, now)) {
            return false;
        }
        StayVerificationCase created = repository.findById(verificationId, worker.companyId())
                .orElseThrow();
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                worker.companyId(),
                actorType,
                actorId,
                role,
                AuditAction.STAY_VERIFICATION_CASE_CREATED,
                AuditTargetType.STAY_VERIFICATION,
                created.stayVerificationId(),
                requestId,
                traceId,
                AUDIT_VERSION,
                "기록상 체류기간 경과를 감지해 긴급 확인 Case를 생성함",
                now
        ));
        return true;
    }
}
