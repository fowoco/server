package com.fowoco.server.workerlink.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerLinkDeliveryStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerLinkDeliveryService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final TaskRepository taskRepository;
    private final WorkerLinkRepository workerLinkRepository;
    private final AuditEventRepository auditRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkDeliveryService(
            TaskRepository taskRepository,
            WorkerLinkRepository workerLinkRepository,
            AuditEventRepository auditRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.workerLinkRepository = workerLinkRepository;
        this.auditRepository = auditRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkerLinkDeliveryResult findCurrent(UUID taskId, ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        taskRepository.findByIdAndCompanyId(taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
        WorkerLink link = workerLinkRepository.findActiveByTaskIdAndCompanyId(taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_RESOURCE_NOT_FOUND));
        return WorkerLinkDeliveryResult.from(link);
    }

    @Transactional
    public WorkerLinkDeliveryResult markSent(
            UUID workerLinkId,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        WorkerLink link = workerLinkRepository.findByIdAndCompanyId(workerLinkId, actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_RESOURCE_NOT_FOUND));
        Instant now = clock.instant();
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_ACTIVE);
        }
        if (link.deliveryStatus() == WorkerLinkDeliveryStatus.SENT) {
            return WorkerLinkDeliveryResult.from(link);
        }

        WorkerLink saved = workerLinkRepository.update(link.markSent(actor.actorId(), now));
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                AuditAction.WORKER_LINK_SENT,
                AuditTargetType.WORKER_LINK,
                workerLinkId,
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "근로자 링크 전달 완료 기록 (taskId=" + link.taskId() + ")",
                now
        ));
        return WorkerLinkDeliveryResult.from(saved);
    }

    private UserRole effectiveRole(ActorContext actor) {
        return actor.roles().stream()
                .min(Comparator.comparingInt(this::rolePriority))
                .orElseThrow();
    }

    private int rolePriority(UserRole role) {
        return switch (role) {
            case ADMIN -> 0;
            case HR -> 1;
            case VIEWER -> 2;
        };
    }
}
