package com.fowoco.server.worker.archive.application;

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
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.archive.application.error.WorkerArchiveErrorCode;
import com.fowoco.server.worker.archive.application.port.WorkerArchiveRepository;
import com.fowoco.server.worker.archive.domain.WorkerArchive;
import com.fowoco.server.worker.domain.Worker;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerArchiveService {

    private static final String AUDIT_EVENT_VERSION = "1.0";
    private static final int MAX_REASON_LENGTH = 500;

    private final WorkerRepository workerRepository;
    private final WorkerArchiveRepository archiveRepository;
    private final AuditEventRepository auditRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerArchiveService(
            WorkerRepository workerRepository,
            WorkerArchiveRepository archiveRepository,
            AuditEventRepository auditRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerRepository = workerRepository;
        this.archiveRepository = archiveRepository;
        this.auditRepository = auditRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkerArchiveEligibility checkEligibility(UUID workerId, ActorContext actor) {
        bindTenant(actor);
        Worker worker = requireWorker(workerId, actor.companyId());
        return eligibility(worker, DatabaseTimestamp.now(clock));
    }

    @Transactional
    public WorkerArchive archive(
            WorkerArchiveCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        String reason = normalizeReason(command.reason());
        if (!archiveRepository.lockWorker(command.workerId(), actor.companyId())) {
            throw new ApiException(WorkerErrorCode.WORKER_NOT_FOUND);
        }
        Worker worker = requireWorker(command.workerId(), actor.companyId());
        WorkerArchiveEligibility eligibility = eligibility(worker, DatabaseTimestamp.now(clock));

        if (eligibility.blockers().contains(WorkerArchiveBlocker.ALREADY_ARCHIVED)) {
            throw new ApiException(WorkerArchiveErrorCode.WORKER_ALREADY_ARCHIVED);
        }
        if (!eligibility.archivable()) {
            throw new ApiException(WorkerArchiveErrorCode.WORKER_ARCHIVE_NOT_ALLOWED);
        }
        if (worker.version() != command.expectedVersion()) {
            throw new ApiException(WorkerArchiveErrorCode.WORKER_ARCHIVE_VERSION_CONFLICT);
        }

        Instant now = DatabaseTimestamp.nowNotBefore(clock, worker.createdAt());
        if (!archiveRepository.reserveWorkerVersion(
                worker.workerId(), worker.companyId(), command.expectedVersion(), now)) {
            throw new ApiException(WorkerArchiveErrorCode.WORKER_ARCHIVE_VERSION_CONFLICT);
        }

        WorkerArchive archive = new WorkerArchive(
                worker.workerId(),
                worker.companyId(),
                now,
                actor.actorId(),
                reason,
                command.expectedVersion() + 1
        );
        archiveRepository.insert(archive);
        appendAudit(archive, actor, metadata);
        return archive;
    }

    private WorkerArchiveEligibility eligibility(Worker worker, Instant now) {
        List<WorkerArchiveBlocker> blockers = new ArrayList<>();
        if (worker.isCurrentlyEmployed()) {
            blockers.add(WorkerArchiveBlocker.ACTIVE_EMPLOYMENT_STATUS);
        }
        if (archiveRepository.find(worker.workerId(), worker.companyId()).isPresent()) {
            blockers.add(WorkerArchiveBlocker.ALREADY_ARCHIVED);
        }
        blockers.addAll(archiveRepository.findOperationalBlockers(
                worker.workerId(), worker.companyId(), now));
        List<WorkerArchiveBlocker> distinct = blockers.stream().distinct().toList();
        return new WorkerArchiveEligibility(
                worker.workerId(), distinct.isEmpty(), distinct, worker.version());
    }

    private Worker requireWorker(UUID workerId, UUID companyId) {
        return workerRepository.findByWorkerIdAndCompanyId(workerId, companyId)
                .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_NOT_FOUND));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("archive reason must not be blank");
        }
        String normalized = reason.strip();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "archive reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }
        return normalized;
    }

    private void appendAudit(
            WorkerArchive archive,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                archive.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                AuditAction.WORKER_ARCHIVED,
                AuditTargetType.WORKER,
                archive.workerId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "근로자를 운영 목록에서 안전 보관 처리함",
                archive.archivedAt()
        ));
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

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }
}
