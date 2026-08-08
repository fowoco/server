package com.fowoco.server.workerlink.application;

import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.CompanySettings;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkGenerator;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerLinkService {

    private final TaskRepository taskRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final WorkerLinkRepository workerLinkRepository;
    private final WorkerLinkGenerator workerLinkGenerator;
    private final WorkerLinkHasher workerLinkHasher;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkService(
            TaskRepository taskRepository,
            ApprovalRequestRepository approvalRequestRepository,
            CompanySettingsRepository companySettingsRepository,
            WorkerLinkRepository workerLinkRepository,
            WorkerLinkGenerator workerLinkGenerator,
            WorkerLinkHasher workerLinkHasher,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.workerLinkRepository = workerLinkRepository;
        this.workerLinkGenerator = workerLinkGenerator;
        this.workerLinkHasher = workerLinkHasher;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerLinkIssueResult issue(WorkerLinkIssueCommand command, ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());

        Task task = taskRepository.findByIdAndCompanyId(command.taskId(), actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.TASK_NOT_FOUND));
        if (task.workerId() == null) {
            throw new ApiException(WorkerLinkErrorCode.TASK_WORKER_TARGET_REQUIRED);
        }

        ApprovalRequest approval = approvalRequestRepository
                .findLatestApprovedByTaskIdAndCompanyId(command.taskId(), actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.TASK_NOT_APPROVED));

        if (!approval.isValidFor(task.contentRevision(), task.criticalFingerprint())) {
            throw new ApiException(WorkerLinkErrorCode.TASK_NOT_APPROVED);
        }

        String idempotencyKeyHash = workerLinkHasher.hash(command.idempotencyKey());
        Optional<WorkerLink> existingByIdempotency = workerLinkRepository
                .findByTaskIdAndIdempotencyKey(command.taskId(), idempotencyKeyHash);
        if (existingByIdempotency.isPresent()) {
            WorkerLink previous = existingByIdempotency.get();
            return new WorkerLinkIssueResult(
                    previous.workerLinkId(),
                    null,
                    previous.expiresAt(),
                    previous.deliveryStatus(),
                    previous.sentAt(),
                    true
            );
        }

        Instant now = clock.instant();
        Optional<WorkerLink> existingActive = workerLinkRepository
                .findActiveByTaskIdAndCompanyId(command.taskId(), actor.companyId());

        WorkerLink previousLink = null;
        if (existingActive.isPresent()) {
            if (!command.rotateExisting()) {
                throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_ISSUANCE_CONFLICT);
            }
            previousLink = existingActive.get();
            workerLinkRepository.update(previousLink.revoke(now));
        }

        long hours = resolveExpiryHours(command, actor);
        Instant expiresAt = now.plus(Duration.ofHours(hours));

        WorkerLinkGenerator.GeneratedWorkerLinkToken generated = workerLinkGenerator.generate();

        WorkerLink workerLink = WorkerLink.issue(
                uuidGenerator.generate(),
                command.taskId(),
                actor.companyId(),
                generated.tokenHash(),
                expiresAt,
                actor.actorId(),
                previousLink != null ? previousLink.workerLinkId() : null,
                idempotencyKeyHash,
                now
        );
        workerLinkRepository.insert(workerLink);

        return new WorkerLinkIssueResult(
                workerLink.workerLinkId(),
                generated.rawValue(),
                expiresAt,
                workerLink.deliveryStatus(),
                workerLink.sentAt(),
                false
        );
    }

    private long resolveExpiryHours(WorkerLinkIssueCommand command, ActorContext actor) {
        long hours = command.expiresInHours() != null
                ? command.expiresInHours()
                : companySettingsRepository.findByCompanyId(actor.companyId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Persisted company settings are missing for company "
                                        + actor.companyId()
                        ))
                        .linkExpiryHours();
        if (hours < CompanySettings.MIN_LINK_EXPIRY_HOURS
                || hours > CompanySettings.MAX_LINK_EXPIRY_HOURS) {
            throw new IllegalArgumentException("expiresInHours must be between 1 and 168");
        }
        return hours;
    }
}
