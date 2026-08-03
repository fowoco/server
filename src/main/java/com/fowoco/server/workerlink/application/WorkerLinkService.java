package com.fowoco.server.workerlink.application;

import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.task.application.error.TaskErrorCode;
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

    private static final long DEFAULT_EXPIRES_IN_HOURS = 72L;

    private final TaskRepository taskRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final WorkerLinkRepository workerLinkRepository;
    private final WorkerLinkGenerator workerLinkGenerator;
    private final WorkerLinkHasher workerLinkHasher;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkService(
            TaskRepository taskRepository,
            ApprovalRequestRepository approvalRequestRepository,
            WorkerLinkRepository workerLinkRepository,
            WorkerLinkGenerator workerLinkGenerator,
            WorkerLinkHasher workerLinkHasher,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.workerLinkRepository = workerLinkRepository;
        this.workerLinkGenerator = workerLinkGenerator;
        this.workerLinkHasher = workerLinkHasher;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerLinkIssueResult issue(WorkerLinkIssueCommand command) {
        Task task = taskRepository.findByIdAndCompanyId(command.taskId(), command.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.TASK_NOT_FOUND));

        ApprovalRequest approval = approvalRequestRepository
                .findLatestApprovedByTaskIdAndCompanyId(command.taskId(), command.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.TASK_NOT_APPROVED));

        if (!approval.isValidFor(task.contentRevision(), task.criticalFingerprint())) {
            throw new ApiException(WorkerLinkErrorCode.TASK_NOT_APPROVED);
        }

        String idempotencyKeyHash = workerLinkHasher.hash(command.idempotencyKey());
        Optional<WorkerLink> existingByIdempotency = workerLinkRepository
                .findByTaskIdAndIdempotencyKey(command.taskId(), idempotencyKeyHash);
        if (existingByIdempotency.isPresent()) {
            WorkerLink previous = existingByIdempotency.get();
            return new WorkerLinkIssueResult(previous.tokenHash(), previous.expiresAt());
        }

        Instant now = clock.instant();
        Optional<WorkerLink> existingActive = workerLinkRepository
                .findActiveByTaskIdAndCompanyId(command.taskId(), command.companyId());

        WorkerLink previousLink = null;
        if (existingActive.isPresent()) {
            if (!command.rotateExisting()) {
                throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_ISSUANCE_CONFLICT);
            }
            previousLink = existingActive.get();
            workerLinkRepository.update(previousLink.revoke(now));
        }

        long hours = command.expiresInHours() != null ? command.expiresInHours() : DEFAULT_EXPIRES_IN_HOURS;
        Instant expiresAt = now.plus(Duration.ofHours(hours));

        WorkerLinkGenerator.GeneratedWorkerLinkToken generated = workerLinkGenerator.generate();

        WorkerLink workerLink = WorkerLink.issue(
                uuidGenerator.generate(),
                command.taskId(),
                command.companyId(),
                generated.tokenHash(),
                expiresAt,
                command.issuedBy(),
                previousLink != null ? previousLink.workerLinkId() : null,
                idempotencyKeyHash,
                now
        );
        workerLinkRepository.insert(workerLink);

        return new WorkerLinkIssueResult(generated.rawValue(), expiresAt);
    }
}
