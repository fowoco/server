package com.fowoco.server.stayverification.application;

import static com.fowoco.server.stayverification.application.error.StayVerificationErrorCode.STAY_VERIFICATION_DOCUMENT_NOT_FOUND;
import static com.fowoco.server.stayverification.application.error.StayVerificationErrorCode.STAY_VERIFICATION_EMPLOYMENT_END_NOTE_REQUIRED;
import static com.fowoco.server.stayverification.application.error.StayVerificationErrorCode.STAY_VERIFICATION_EVIDENCE_REQUIRED;
import static com.fowoco.server.stayverification.application.error.StayVerificationErrorCode.STAY_VERIFICATION_NEW_EXPIRY_REQUIRED;
import static com.fowoco.server.stayverification.application.error.StayVerificationErrorCode.STAY_VERIFICATION_NOT_FOUND;
import static com.fowoco.server.stayverification.application.error.StayVerificationErrorCode.STAY_VERIFICATION_PENDING_DETAILS_REQUIRED;
import static com.fowoco.server.stayverification.application.error.StayVerificationErrorCode.STAY_VERIFICATION_VERSION_CONFLICT;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository;
import com.fowoco.server.stayverification.application.port.StayVerificationRepository.ExpiredWorker;
import com.fowoco.server.stayverification.application.port.ExpiredStayCandidateReader;
import com.fowoco.server.stayverification.domain.StayVerificationCase;
import com.fowoco.server.stayverification.domain.StayVerificationStatus;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class StayVerificationService {

    private static final String AUDIT_VERSION = "1";
    private static final String DAILY_SCAN_REQUEST_ID = "stay-verification-daily-scan";

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final StayVerificationRepository repository;
    private final ExpiredStayCandidateReader expiredStayCandidateReader;
    private final StayVerificationCaseCreationTransaction caseCreationTransaction;
    private final WorkerRepository workerRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public StayVerificationService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            StayVerificationRepository repository,
            ExpiredStayCandidateReader expiredStayCandidateReader,
            StayVerificationCaseCreationTransaction caseCreationTransaction,
            WorkerRepository workerRepository,
            WorkerDocumentRepository workerDocumentRepository,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.repository = repository;
        this.expiredStayCandidateReader = expiredStayCandidateReader;
        this.caseCreationTransaction = caseCreationTransaction;
        this.workerRepository = workerRepository;
        this.workerDocumentRepository = workerDocumentRepository;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public int scanCompany(ActorContext actor, RequestMetadata metadata) {
        bindTenant(actor);
        actorAuthorizer.requireHrWrite(actor);
        Instant now = DatabaseTimestamp.now(clock);
        return createCases(
                repository.findExpiredWorkers(actor.companyId(), LocalDate.now(clock)),
                now,
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                metadata.requestId(),
                metadata.traceId()
        );
    }

    @Transactional
    public int scanAllCompanies() {
        Instant now = DatabaseTimestamp.now(clock);
        return createCases(
                expiredStayCandidateReader.findExpiredWorkers(LocalDate.now(clock)),
                now,
                ActorType.SYSTEM_RULE,
                null,
                null,
                DAILY_SCAN_REQUEST_ID,
                null
        );
    }

    @Transactional(readOnly = true)
    public List<StayVerificationCase> findAll(StayVerificationStatus status, ActorContext actor) {
        bindTenant(actor);
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        return repository.findAll(actor.companyId(), status);
    }

    @Transactional
    public StayVerificationCase update(
            StayVerificationCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        actorAuthorizer.requireHrWrite(actor);
        StayVerificationCase current = repository
                .findById(command.stayVerificationId(), actor.companyId())
                .orElseThrow(() -> new ApiException(STAY_VERIFICATION_NOT_FOUND));
        validate(command, current, actor.companyId());

        Instant now = DatabaseTimestamp.now(clock);
        if (!repository.update(command, actor.companyId(), now, now)) {
            if (repository.findById(command.stayVerificationId(), actor.companyId()).isEmpty()) {
                throw new ApiException(STAY_VERIFICATION_NOT_FOUND);
            }
            throw new ApiException(STAY_VERIFICATION_VERSION_CONFLICT);
        }
        if (command.status() == StayVerificationStatus.APPROVED) {
            updateWorkerExpiry(current, command.newStayExpiryDate(), now);
        }
        StayVerificationCase updated = repository
                .findById(command.stayVerificationId(), actor.companyId())
                .orElseThrow(() -> new ApiException(STAY_VERIFICATION_NOT_FOUND));
        appendAudit(
                updated,
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                AuditAction.STAY_VERIFICATION_STATUS_UPDATED,
                changeSummary(current, updated),
                metadata.requestId(),
                metadata.traceId(),
                now
        );
        return updated;
    }

    private int createCases(
            List<ExpiredWorker> workers,
            Instant now,
            ActorType actorType,
            UUID actorId,
            UserRole role,
            String requestId,
            String traceId
    ) {
        int created = 0;
        for (ExpiredWorker worker : workers) {
            try {
                if (caseCreationTransaction.createIfAbsent(
                        worker,
                        now,
                        actorType,
                        actorId,
                        role,
                        requestId,
                        traceId
                )) {
                    created++;
                }
            } catch (DataIntegrityViolationException duplicateScan) {
                // 동시 스캔은 UNIQUE(company_id, worker_id, source_stay_expiry_date)가 최종 차단합니다.
            }
        }
        return created;
    }

    private void validate(StayVerificationCommand command, StayVerificationCase current, UUID companyId) {
        boolean hasNote = command.officialConsultationNote() != null
                && !command.officialConsultationNote().isBlank();
        validateDocument(command.extensionReceiptDocumentId(), current.workerId(), companyId);
        validateDocument(command.approvalResultDocumentId(), current.workerId(), companyId);
        switch (command.status()) {
            case APPROVED -> {
                if (command.newStayExpiryDate() == null
                        || !command.newStayExpiryDate().isAfter(current.sourceStayExpiryDate())) {
                    throw new ApiException(STAY_VERIFICATION_NEW_EXPIRY_REQUIRED);
                }
                if (command.approvalResultDocumentId() == null && !hasNote) {
                    throw new ApiException(STAY_VERIFICATION_EVIDENCE_REQUIRED);
                }
            }
            case APPLICATION_PENDING -> {
                boolean missing = command.extensionAppliedAt() == null
                        || command.recheckDate() == null
                        || !command.recheckDate().isAfter(LocalDate.now(clock))
                        || (command.extensionReceiptDocumentId() == null && !hasNote);
                if (missing) {
                    throw new ApiException(STAY_VERIFICATION_PENDING_DETAILS_REQUIRED);
                }
            }
            case EMPLOYMENT_ENDED -> {
                if (command.employmentEndConfirmedAt() == null || !hasNote) {
                    throw new ApiException(STAY_VERIFICATION_EMPLOYMENT_END_NOTE_REQUIRED);
                }
            }
            case NOT_APPLIED -> {
                if (!hasNote) {
                    throw new ApiException(STAY_VERIFICATION_EVIDENCE_REQUIRED);
                }
            }
            case UNKNOWN -> {
                // UNKNOWN은 확인 진행 중 상태이므로 증빙 없이 저장할 수 있습니다.
            }
        }
    }

    private void validateDocument(UUID documentId, UUID workerId, UUID companyId) {
        if (documentId == null) {
            return;
        }
        workerDocumentRepository.findByIdAndWorkerIdAndCompanyId(documentId, workerId, companyId)
                .orElseThrow(() -> new ApiException(STAY_VERIFICATION_DOCUMENT_NOT_FOUND));
    }

    private void updateWorkerExpiry(StayVerificationCase verification, LocalDate newExpiry, Instant now) {
        Worker existing = workerRepository
                .findByWorkerIdAndCompanyId(verification.workerId(), verification.companyId())
                .orElseThrow(() -> new ApiException(STAY_VERIFICATION_NOT_FOUND));
        Worker updated = new Worker(
                existing.workerId(),
                existing.companyId(),
                existing.displayName(),
                existing.nationalityCode(),
                existing.preferredLanguage(),
                existing.workStatus(),
                existing.visaType(),
                newExpiry,
                existing.contractStartDate(),
                existing.contractEndDate(),
                existing.employmentPermitEndDate(),
                existing.employmentActivityEndDate(),
                existing.createdAt(),
                now.isBefore(existing.createdAt()) ? existing.createdAt() : now,
                existing.version()
        );
        workerRepository.update(updated);
    }

    private void appendAudit(
            StayVerificationCase verification,
            ActorType actorType,
            UUID actorId,
            UserRole userRole,
            AuditAction action,
            String summary,
            String requestId,
            String traceId,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                verification.companyId(),
                actorType,
                actorId,
                userRole,
                action,
                AuditTargetType.STAY_VERIFICATION,
                verification.stayVerificationId(),
                requestId,
                traceId,
                AUDIT_VERSION,
                summary,
                now
        ));
    }

    private String changeSummary(StayVerificationCase before, StayVerificationCase after) {
        return "status=" + before.verificationStatus() + "->" + after.verificationStatus()
                + ", new_stay_expiry_date=" + before.newStayExpiryDate()
                + "->" + after.newStayExpiryDate()
                + ", recheck_date=" + before.recheckDate() + "->" + after.recheckDate()
                + ", evidence_fields_changed="
                + (!java.util.Objects.equals(
                        before.extensionReceiptDocumentId(), after.extensionReceiptDocumentId()
                ) || !java.util.Objects.equals(
                        before.approvalResultDocumentId(), after.approvalResultDocumentId()
                ));
    }

    private UserRole effectiveRole(ActorContext actor) {
        return actor.roles().stream()
                .min(Comparator.comparingInt(role -> switch (role) {
                    case ADMIN -> 0;
                    case HR -> 1;
                    case VIEWER -> 2;
                }))
                .orElseThrow();
    }

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }
}
