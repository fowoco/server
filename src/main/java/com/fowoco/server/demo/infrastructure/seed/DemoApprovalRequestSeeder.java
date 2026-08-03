package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ApprovalSeed;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

final class DemoApprovalRequestSeeder {

    private static final String AI_SNAPSHOT_JSON = "{\"summary\":\"demo operational review\"}";
    private static final String HR_SNAPSHOT_JSON = "{\"review\":\"demo operational seed\"}";
    private static final String CHANGED_FIELDS_JSON = "[]";
    private static final String SOURCE_VERSIONS_JSON = "{\"workflow_catalog\":\"0.2.0\"}";

    private final ApprovalRequestRepository approvalRepository;
    private final TaskRepository taskRepository;

    DemoApprovalRequestSeeder(
            ApprovalRequestRepository approvalRepository,
            TaskRepository taskRepository
    ) {
        this.approvalRepository = Objects.requireNonNull(
                approvalRepository,
                "approvalRepository must not be null"
        );
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
    }

    void seed(ApprovalSeed seed, DemoOperationalSeedContext context) {
        Task task = taskRepository.findByIdAndCompanyId(seed.taskId(), context.companyId())
                .orElseThrow(() -> new IllegalStateException("a demo approval task does not exist"));
        Optional<ApprovalRequest> existing = approvalRepository.findByIdAndCompanyId(
                seed.approvalRequestId(),
                context.companyId()
        );
        if (existing.isPresent()) {
            verifyExisting(existing.get(), seed, context, task);
            return;
        }
        Instant requestedAt = context.now().minus(seed.requestedHoursAgo(), ChronoUnit.HOURS);
        ApprovalRequest approval = ApprovalRequest.create(
                seed.approvalRequestId(),
                seed.taskId(),
                context.companyId(),
                task.version(),
                task.contentRevision(),
                task.criticalFingerprint(),
                AI_SNAPSHOT_JSON,
                HR_SNAPSHOT_JSON,
                CHANGED_FIELDS_JSON,
                SOURCE_VERSIONS_JSON,
                context.actorId(),
                requestedAt
        );
        applyOutcome(approval, seed, task, context);
        approvalRepository.save(approval);
    }

    private void applyOutcome(
            ApprovalRequest approval,
            ApprovalSeed seed,
            Task task,
            DemoOperationalSeedContext context
    ) {
        if (seed.status() == ApprovalStatus.PENDING) {
            return;
        }
        Instant outcomeAt = context.now().minus(seed.outcomeHoursAgo(), ChronoUnit.HOURS);
        switch (seed.status()) {
            case APPROVED -> approval.approve(
                    task.version(),
                    task.contentRevision(),
                    task.criticalFingerprint(),
                    task.version(),
                    context.actorId(),
                    seed.reason(),
                    outcomeAt
            );
            case REJECTED -> approval.reject(task.version(), context.actorId(), seed.reason(), outcomeAt);
            case INVALIDATED -> approval.invalidate(seed.reason(), outcomeAt);
            case PENDING -> throw new IllegalStateException("pending approval must not have an outcome");
        }
    }

    void verifyExisting(
            ApprovalRequest approval,
            ApprovalSeed seed,
            DemoOperationalSeedContext context,
            Task task
    ) {
        if (!seed.approvalRequestId().equals(approval.approvalRequestId())
                || !seed.taskId().equals(approval.taskId())
                || !context.companyId().equals(approval.companyId())
                || seed.status() != approval.status()
                || task.version() != approval.targetTaskVersion()
                || task.contentRevision() != approval.targetContentRevision()
                || !task.criticalFingerprint().equals(approval.targetFingerprint())
                || !context.actorId().equals(approval.requestedBy())
                || !matchesOutcome(approval, seed, context)) {
            throw new IllegalStateException(
                    "a reserved demo approval id already belongs to different approval data"
            );
        }
    }

    private boolean matchesOutcome(
            ApprovalRequest approval,
            ApprovalSeed seed,
            DemoOperationalSeedContext context
    ) {
        return switch (seed.status()) {
            case PENDING -> approval.decidedBy() == null && approval.invalidatedAt() == null;
            case APPROVED, REJECTED -> context.actorId().equals(approval.decidedBy())
                    && seed.reason().equals(approval.decisionReason())
                    && approval.decidedAt() != null;
            case INVALIDATED -> seed.reason().equals(approval.invalidationReason())
                    && approval.invalidatedAt() != null;
        };
    }
}
