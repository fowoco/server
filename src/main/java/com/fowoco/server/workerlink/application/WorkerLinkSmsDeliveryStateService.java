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
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerLinkDeliveryStatus;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerLinkSmsDeliveryStateService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final WorkerLinkRepository workerLinkRepository;
    private final AuditEventRepository auditRepository;
    private final WorkerLinkHasher workerLinkHasher;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkSmsDeliveryStateService(
            WorkerLinkRepository workerLinkRepository,
            AuditEventRepository auditRepository,
            WorkerLinkHasher workerLinkHasher,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerLinkRepository = workerLinkRepository;
        this.auditRepository = auditRepository;
        this.workerLinkHasher = workerLinkHasher;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerLinkSmsDeliveryStart begin(
            WorkerLinkSmsDeliveryCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        WorkerLink link = findForUpdate(command.workerLinkId(), actor);
        Instant now = now();
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_ACTIVE);
        }
        verifyMatches(link.idempotencyKey(), workerLinkHasher.hash(command.idempotencyKey()));
        verifyMatches(link.tokenHash(), workerLinkHasher.hash(command.rawToken()));

        if (link.deliveryStatus() == WorkerLinkDeliveryStatus.SENT) {
            return WorkerLinkSmsDeliveryStart.alreadySent(WorkerLinkDeliveryResult.from(link));
        }
        if (link.deliveryStatus() != WorkerLinkDeliveryStatus.NOT_SENT) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_DELIVERY_REVIEW_REQUIRED);
        }

        WorkerLink saved = workerLinkRepository.update(link.markSending(now));
        appendAudit(
                saved,
                actor,
                metadata,
                AuditAction.WORKER_LINK_SMS_DELIVERY_STARTED,
                "근로자 링크 SMS 발송 시작",
                now
        );
        return WorkerLinkSmsDeliveryStart.ready(WorkerLinkDeliveryResult.from(saved));
    }

    @Transactional
    public WorkerLinkDeliveryResult markAccepted(
            UUID workerLinkId,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        WorkerLink link = findForUpdate(workerLinkId, actor);
        if (link.deliveryStatus() == WorkerLinkDeliveryStatus.SENT) {
            return WorkerLinkDeliveryResult.from(link);
        }
        if (link.deliveryStatus() != WorkerLinkDeliveryStatus.SENDING) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_DELIVERY_REVIEW_REQUIRED);
        }

        Instant acceptedAt = now();
        WorkerLink saved = workerLinkRepository.update(link.markSent(actor.actorId(), acceptedAt));
        appendAudit(
                saved,
                actor,
                metadata,
                AuditAction.WORKER_LINK_SENT,
                "SOLAPI가 근로자 링크 SMS 발송 요청을 접수함",
                acceptedAt
        );
        return WorkerLinkDeliveryResult.from(saved);
    }

    @Transactional
    public void markRejected(
            UUID workerLinkId,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        WorkerLink link = findForUpdate(workerLinkId, actor);
        if (link.deliveryStatus() != WorkerLinkDeliveryStatus.SENDING) {
            return;
        }
        Instant now = now();
        WorkerLink saved = workerLinkRepository.update(link.markNotSentAfterRejectedDelivery(now));
        appendAudit(
                saved,
                actor,
                metadata,
                AuditAction.WORKER_LINK_SMS_DELIVERY_FAILED,
                "SOLAPI가 근로자 링크 SMS 발송 요청을 접수하지 않음",
                now
        );
    }

    @Transactional
    public void markReviewRequired(
            UUID workerLinkId,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        WorkerLink link = findForUpdate(workerLinkId, actor);
        if (link.deliveryStatus() != WorkerLinkDeliveryStatus.SENDING) {
            return;
        }
        Instant now = now();
        WorkerLink saved = workerLinkRepository.update(link.markDeliveryReviewRequired(now));
        appendAudit(
                saved,
                actor,
                metadata,
                AuditAction.WORKER_LINK_SMS_DELIVERY_REVIEW_REQUIRED,
                "SOLAPI 발송 결과 불명확으로 담당자 확인 필요",
                now
        );
    }

    private WorkerLink findForUpdate(UUID workerLinkId, ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        return workerLinkRepository.findByIdAndCompanyIdForUpdate(workerLinkId, actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_RESOURCE_NOT_FOUND));
    }

    private void appendAudit(
            WorkerLink link,
            ActorContext actor,
            RequestMetadata metadata,
            AuditAction action,
            String summary,
            Instant occurredAt
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                action,
                AuditTargetType.WORKER_LINK,
                link.workerLinkId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                summary + " (taskId=" + link.taskId() + ")",
                occurredAt
        ));
    }

    private void verifyMatches(String expectedHash, String actualHash) {
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_REQUEST_MISMATCH);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
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
