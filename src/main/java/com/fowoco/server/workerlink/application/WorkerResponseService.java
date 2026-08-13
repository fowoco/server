package com.fowoco.server.workerlink.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.error.WorkerResponseUploadAlreadyLinkedException;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkTenantBootstrap;
import com.fowoco.server.workerlink.application.port.WorkerResponseRepository;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerResponse;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerResponseService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final WorkerLinkTenantBootstrap workerLinkTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final WorkerLinkRepository workerLinkRepository;
    private final WorkerResponseRepository workerResponseRepository;
    private final DocumentRequestDraftRepository documentRequestDraftRepository;
    private final WorkerRequestedActionResolver requestedActionResolver;
    private final WorkerResponsePayloadCodec payloadCodec;
    private final WorkerLinkHasher workerLinkHasher;
    private final StoredFileRepository storedFileRepository;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final com.fowoco.server.task.application.port.TaskRepository taskRepository;
    private final com.fowoco.server.reliability.application.port.DomainEventPublisher eventPublisher;

    public WorkerResponseService(
            WorkerLinkTenantBootstrap workerLinkTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerLinkRepository workerLinkRepository,
            WorkerResponseRepository workerResponseRepository,
            DocumentRequestDraftRepository documentRequestDraftRepository,
            WorkerRequestedActionResolver requestedActionResolver,
            WorkerResponsePayloadCodec payloadCodec,
            WorkerLinkHasher workerLinkHasher,
            StoredFileRepository storedFileRepository,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock,
            com.fowoco.server.task.application.port.TaskRepository taskRepository,
            com.fowoco.server.reliability.application.port.DomainEventPublisher eventPublisher
    ) {
        this.workerLinkTenantBootstrap = workerLinkTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerLinkRepository = workerLinkRepository;
        this.workerResponseRepository = workerResponseRepository;
        this.documentRequestDraftRepository = documentRequestDraftRepository;
        this.requestedActionResolver = requestedActionResolver;
        this.payloadCodec = payloadCodec;
        this.workerLinkHasher = workerLinkHasher;
        this.storedFileRepository = storedFileRepository;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public WorkerResponseSubmitResult submit(WorkerResponseSubmitCommand command, RequestMetadata metadata) {
        String tokenHash = workerLinkHasher.hash(command.rawToken());

        UUID companyId = workerLinkTenantBootstrap
                .findCompanyIdByWorkerLinkTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);

        WorkerLink link = workerLinkRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        Instant now = DatabaseTimestamp.nowNotBefore(clock, link.createdAt());
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND);
        }

        com.fowoco.server.task.domain.Task task = taskRepository
                .findByIdAndCompanyId(link.taskId(), companyId)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));
        List<UUID> uploadIds = command.uploadIds() != null ? List.copyOf(command.uploadIds()) : List.of();
        Map<String, String> submittedAnswers = command.answers() == null
                ? Map.of()
                : new LinkedHashMap<>(command.answers());
        String requestFingerprint = payloadCodec.fingerprint(
                command.responseType(), command.message(), uploadIds, submittedAnswers
        );
        Optional<WorkerResponse> existing = workerResponseRepository
                .findByWorkerLinkIdAndIdempotencyKey(link.workerLinkId(), command.idempotencyKey());
        if (existing.isPresent()) {
            WorkerResponse previous = existing.get();
            if (previous.requestFingerprint() != null
                    && !previous.requestFingerprint().equals(requestFingerprint)) {
                throw new ApiException(WorkerLinkErrorCode.WORKER_RESPONSE_IDEMPOTENCY_CONFLICT);
            }
            return new WorkerResponseSubmitResult(previous.responseId(), previous.receivedAt());
        }
        Map<String, String> answers = validateAnswers(command, task, companyId);
        validateResponseShape(command.responseType(), answers);

        for (UUID uploadId : uploadIds) {
            StoredFile storedFile = storedFileRepository.findByIdAndCompanyId(uploadId, companyId)
                    .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE));
            if (!storedFile.verified() || !link.taskId().equals(storedFile.taskId())) {
                throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
            }
            if (workerResponseRepository.isUploadAlreadyLinked(uploadId, companyId)) {
                throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
            }
        }

        UUID responseId = uuidGenerator.generate();
        WorkerResponse response = WorkerResponse.create(
                responseId,
                link.workerLinkId(),
                companyId,
                command.responseType(),
                command.message(),
                payloadCodec.encodeAnswers(answers),
                command.idempotencyKey(),
                requestFingerprint,
                now
        );
        workerResponseRepository.insert(response);

        for (UUID uploadId : uploadIds) {
            try {
                workerResponseRepository.linkUpload(responseId, uploadId, companyId);
            } catch (WorkerResponseUploadAlreadyLinkedException exception) {
                throw new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE);
            }
        }

        if (requiresHrReview(command.responseType())) {
            workerLinkRepository.update(link.markNeedsFollowup(now));
        }

        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                companyId,
                ActorType.WORKER_LINK,
                null,
                null,
                AuditAction.WORKER_LINK_RESPONSE_SUBMITTED,
                AuditTargetType.TASK,
                link.taskId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                auditSummary(command.responseType(), answers),
                now
        ));
        if (command.responseType() == WorkerResponseType.DOCUMENT_SUBMITTED
                || command.responseType() == WorkerResponseType.SLOT_ANSWERS_SUBMITTED) {
            publishContinuationEvent(link, responseId, task, companyId, command.responseType(), now);
        }
        return new WorkerResponseSubmitResult(responseId, now);
    }

    private Map<String, String> validateAnswers(
            WorkerResponseSubmitCommand command,
            com.fowoco.server.task.domain.Task task,
            UUID companyId
    ) {
        Map<String, String> submitted = command.answers() == null ? Map.of() : command.answers();
        if (submitted.isEmpty()) {
            return Map.of();
        }
        DocumentRequestDraft draft = documentRequestDraftRepository
                .findByTaskIdAndCompanyId(task.taskId(), companyId)
                .filter(value -> value.message() != null && !value.message().isBlank())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_CONTENT_NOT_READY));
        try {
            return requestedActionResolver.validateAnswers(
                    submitted,
                    requestedActionResolver.resolve(task, draft)
            );
        } catch (WorkerRequestedActionResolver.InvalidWorkerSlotAnswerException exception) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_SLOT_ANSWER_INVALID);
        }
    }

    private void validateResponseShape(WorkerResponseType responseType, Map<String, String> answers) {
        boolean slotResponse = responseType == WorkerResponseType.SLOT_ANSWERS_SUBMITTED;
        if (slotResponse != !answers.isEmpty()) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_SLOT_ANSWER_INVALID);
        }
    }

    private String auditSummary(WorkerResponseType responseType, Map<String, String> answers) {
        if (answers.isEmpty()) {
            return "근로자 응답 제출: " + responseType;
        }
        return "근로자 Slot 답변 제출: " + answers.keySet().stream().sorted().toList();
    }

    private void publishContinuationEvent(
            WorkerLink link,
            UUID responseId,
            com.fowoco.server.task.domain.Task task,
            UUID companyId,
            WorkerResponseType responseType,
            Instant now
    ) {
        String eventType = responseType == WorkerResponseType.SLOT_ANSWERS_SUBMITTED
                ? WorkerResponseDomainEvents.SLOT_ANSWERS_SUBMITTED
                : WorkerResponseDomainEvents.DOCUMENT_SUBMITTED;
        eventPublisher.publish(WorkerResponseDomainEvents.submitted(
                uuidGenerator.generate(), responseId, task, companyId, link.issuedBy(), eventType, now
        ));
    }

    private boolean requiresHrReview(WorkerResponseType responseType) {
        return responseType != WorkerResponseType.ACKNOWLEDGED;
    }
}
