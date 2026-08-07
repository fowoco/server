package com.fowoco.server.document.application;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentType;
import com.fowoco.server.aiintegration.application.ocr.AiOcrFile;
import com.fowoco.server.aiintegration.application.ocr.AiOcrPassportCountryCodeResolver;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.port.AiOcrClient;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.error.DocumentErrorCode;
import com.fowoco.server.document.application.port.DocumentOcrRunRepository;
import com.fowoco.server.document.application.port.OcrResultCipher;
import com.fowoco.server.document.domain.DocumentOcrReviewDecision;
import com.fowoco.server.document.domain.DocumentOcrRun;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentOcrService {

    private static final Logger log = LoggerFactory.getLogger(DocumentOcrService.class);
    private static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    private static final String AUDIT_EVENT_VERSION = "1.0";

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final WorkerRepository workerRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileStorage fileStorage;
    private final DocumentOcrRunRepository ocrRunRepository;
    private final OcrResultCipher resultCipher;
    private final AiOcrClient aiOcrClient;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Executor taskExecutor;
    private final AiOcrPassportCountryCodeResolver countryCodeResolver =
            new AiOcrPassportCountryCodeResolver();

    public DocumentOcrService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            StoredFileRepository storedFileRepository,
            FileStorage fileStorage,
            DocumentOcrRunRepository ocrRunRepository,
            OcrResultCipher resultCipher,
            AiOcrClient aiOcrClient,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Qualifier("documentOcrTaskExecutor") Executor taskExecutor
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerDocumentRepository = workerDocumentRepository;
        this.workerRepository = workerRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileStorage = fileStorage;
        this.ocrRunRepository = ocrRunRepository;
        this.resultCipher = resultCipher;
        this.aiOcrClient = aiOcrClient;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
    }

    public DocumentOcrRunResult create(
            UUID documentId,
            String idempotencyKey,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        requireFeatureEnabled();
        String keyHash = sha256(normalizeIdempotencyKey(idempotencyKey));
        String requestHash = sha256(documentId.toString());
        Creation creation;
        try {
            creation = transactionTemplate.execute(status -> createInTransaction(
                    documentId, keyHash, requestHash, actor, metadata
            ));
        } catch (DataIntegrityViolationException conflict) {
            creation = transactionTemplate.execute(status -> replayAfterConflict(
                    keyHash, requestHash, actor.companyId()
            ));
        }
        if (creation == null) {
            throw new IllegalStateException("OCR creation transaction returned no result");
        }
        if (creation.newlyCreated()) {
            schedule(creation.run().ocrRunId(), creation.run().companyId());
        }
        return result(creation.run(), !creation.newlyCreated());
    }

    public DocumentOcrRunResult findById(
            UUID documentId,
            UUID ocrRunId,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        requireFeatureEnabled();
        return requiredTransaction(() -> {
            bindTenant(actor.companyId());
            requireDocument(documentId, actor.companyId());
            DocumentOcrRun run = ocrRunRepository.findByIdAndCompanyId(ocrRunId, actor.companyId())
                    .filter(found -> found.workerDocumentId().equals(documentId))
                    .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_RUN_NOT_FOUND));
            DocumentOcrRunResult result = result(run, false);
            auditResultViewIfSensitive(run, actor, metadata);
            return result;
        });
    }

    public DocumentOcrRunResult findLatest(
            UUID documentId,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        requireFeatureEnabled();
        return requiredTransaction(() -> {
            bindTenant(actor.companyId());
            requireDocument(documentId, actor.companyId());
            DocumentOcrRun run = ocrRunRepository
                    .findLatestByDocumentIdAndCompanyId(documentId, actor.companyId())
                    .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_RUN_NOT_FOUND));
            DocumentOcrRunResult result = result(run, false);
            auditResultViewIfSensitive(run, actor, metadata);
            return result;
        });
    }

    public DocumentOcrRunResult review(
            UUID documentId,
            UUID ocrRunId,
            DocumentOcrReviewCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireHrWrite(actor);
        requireFeatureEnabled();
        return requiredTransaction(() -> {
            bindTenant(actor.companyId());
            requireDocument(documentId, actor.companyId());
            DocumentOcrRun current = ocrRunRepository.findByIdAndCompanyId(ocrRunId, actor.companyId())
                    .filter(found -> found.workerDocumentId().equals(documentId))
                    .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_RUN_NOT_FOUND));
            if (current.version() != command.expectedVersion()) {
                throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_VERSION_CONFLICT);
            }
            if (!current.status().isReviewable()) {
                throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_NOT_REVIEWABLE);
            }
            String reason = normalizeReviewReason(command.decision(), command.reason());
            DocumentOcrRun saved = ocrRunRepository.update(
                    current.review(command.decision(), actor.actorId(), reason, clock.instant())
            );
            appendHumanAudit(
                    saved,
                    actor,
                    command.decision() == DocumentOcrReviewDecision.APPROVE
                            ? AuditAction.DOCUMENT_OCR_APPROVED
                            : AuditAction.DOCUMENT_OCR_REJECTED,
                    metadata,
                    "OCR 결과 " + (command.decision() == DocumentOcrReviewDecision.APPROVE ? "승인" : "반려")
            );
            return result(saved, false);
        });
    }

    private Creation createInTransaction(
            UUID documentId,
            String keyHash,
            String requestHash,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor.companyId());
        DocumentOcrRun existing = ocrRunRepository
                .findByIdempotencyKeyHashAndCompanyId(keyHash, actor.companyId())
                .orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }
        WorkerDocument document = requireDocument(documentId, actor.companyId());
        AiOcrDocumentType aiDocumentType = toAiDocumentType(document.documentType());
        UUID fileId = document.fileId();
        if (fileId == null) {
            throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_FILE_REQUIRED);
        }
        StoredFile file = storedFileRepository.findByIdAndCompanyId(fileId, actor.companyId())
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_FILE_REQUIRED));
        if (file.workerId() != null && !document.workerId().equals(file.workerId())) {
            throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_FILE_MISMATCH);
        }
        String countryCode = null;
        if (aiDocumentType == AiOcrDocumentType.PASSPORT_COPY) {
            Worker worker = workerRepository
                    .findByWorkerIdAndCompanyId(document.workerId(), actor.companyId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
            countryCode = resolveCountryCode(worker.nationalityCode());
        }
        Instant now = clock.instant();
        DocumentOcrRun run = DocumentOcrRun.create(
                uuidGenerator.generate(),
                actor.companyId(),
                documentId,
                fileId,
                actor.actorId(),
                uuidGenerator.generate(),
                keyHash,
                requestHash,
                document.documentType(),
                countryCode,
                now
        );
        ocrRunRepository.insert(run);
        appendHumanAudit(run, actor, AuditAction.DOCUMENT_OCR_REQUESTED, metadata, "문서 OCR 실행 요청");
        return new Creation(run, true);
    }

    private Creation replayAfterConflict(String keyHash, String requestHash, UUID companyId) {
        bindTenant(companyId);
        DocumentOcrRun existing = ocrRunRepository
                .findByIdempotencyKeyHashAndCompanyId(keyHash, companyId)
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_IDEMPOTENCY_CONFLICT));
        return replay(existing, requestHash);
    }

    private Creation replay(DocumentOcrRun existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_IDEMPOTENCY_CONFLICT);
        }
        return new Creation(existing, false);
    }

    private void schedule(UUID ocrRunId, UUID companyId) {
        try {
            taskExecutor.execute(() -> execute(ocrRunId, companyId));
        } catch (RuntimeException exception) {
            markFailed(ocrRunId, companyId, AiRuntimeFailureCode.RUNTIME_UNAVAILABLE);
        }
    }

    private void execute(UUID ocrRunId, UUID companyId) {
        ExecutionInput input;
        try {
            input = requiredTransaction(() -> prepareExecution(ocrRunId, companyId));
            byte[] content = readFile(input.file());
            AiOcrRequest request = new AiOcrRequest(
                    input.run().runtimeRequestId(),
                    input.run().workerDocumentId(),
                    toAiDocumentType(input.run().documentType()),
                    input.run().countryCode(),
                    new AiOcrFile(input.file().name(), input.file().mimeType(), content)
            );
            AiOcrResponse response = aiOcrClient.recognize(request, AiRuntimeCallContext.withoutTrace());
            byte[] plaintext = serializePayload(response);
            String ciphertext = resultCipher.encrypt(plaintext, companyId, ocrRunId);
            requiredTransaction(() -> {
                bindTenant(companyId);
                DocumentOcrRun current = requireRun(ocrRunId, companyId);
                DocumentOcrRun saved = ocrRunRepository.update(current.complete(
                        response.status(), ciphertext, resultCipher.keyVersion(), clock.instant()
                ));
                appendSystemAudit(saved, AuditAction.DOCUMENT_OCR_COMPLETED, "문서 OCR 실행 완료");
                return null;
            });
        } catch (RuntimeException exception) {
            AiRuntimeFailureCode failureCode = failureCode(exception);
            log.warn("Document OCR failed. ocrRunId={}, failureCode={}", ocrRunId, failureCode);
            markFailed(ocrRunId, companyId, failureCode);
        }
    }

    private ExecutionInput prepareExecution(UUID ocrRunId, UUID companyId) {
        bindTenant(companyId);
        DocumentOcrRun current = requireRun(ocrRunId, companyId);
        DocumentOcrRun running = ocrRunRepository.update(current.start(clock.instant()));
        StoredFile file = storedFileRepository.findByIdAndCompanyId(running.storedFileId(), companyId)
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_FILE_REQUIRED));
        return new ExecutionInput(running, file);
    }

    private void markFailed(UUID ocrRunId, UUID companyId, AiRuntimeFailureCode failureCode) {
        try {
            requiredTransaction(() -> {
                bindTenant(companyId);
                DocumentOcrRun current = requireRun(ocrRunId, companyId);
                if (current.status().hasResult() || current.status() == com.fowoco.server.document.domain.DocumentOcrRunStatus.FAILED) {
                    return null;
                }
                DocumentOcrRun saved = ocrRunRepository.update(current.fail(failureCode.name(), clock.instant()));
                appendSystemAudit(saved, AuditAction.DOCUMENT_OCR_FAILED, "문서 OCR 실행 실패: " + failureCode.name());
                return null;
            });
        } catch (RuntimeException persistenceFailure) {
            log.error("Failed to persist OCR failure state. ocrRunId={}", ocrRunId, persistenceFailure);
        }
    }

    private DocumentOcrRunResult result(DocumentOcrRun run, boolean alreadyRequested) {
        DocumentOcrResultPayload payload = null;
        if (run.status().hasResult()) {
            byte[] plaintext = resultCipher.decrypt(run.resultCiphertext(), run.companyId(), run.ocrRunId());
            try {
                payload = objectMapper.readValue(plaintext, DocumentOcrResultPayload.class);
            } catch (JacksonException exception) {
                throw new IllegalStateException("stored OCR result is invalid", exception);
            }
        }
        return new DocumentOcrRunResult(run, payload, alreadyRequested);
    }

    private byte[] serializePayload(AiOcrResponse response) {
        DocumentOcrResultPayload payload = new DocumentOcrResultPayload(
                response.matchedTemplateId(),
                response.documentSide(),
                response.fields(),
                response.fieldConfidences(),
                response.reviewReasons()
        );
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OCR result serialization failed", exception);
        }
    }

    private byte[] readFile(StoredFile file) {
        try (InputStream input = fileStorage.open(file.storageKey())
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_FILE_REQUIRED))) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES || bytes.length != file.size()) {
                throw new AiRuntimeContractException(
                        AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                        "OCR file size does not match stored metadata."
                );
            }
            return bytes;
        } catch (IOException exception) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "OCR source file could not be read.",
                    exception
            );
        }
    }

    private WorkerDocument requireDocument(UUID documentId, UUID companyId) {
        return workerDocumentRepository.findByIdAndCompanyId(documentId, companyId)
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }

    private DocumentOcrRun requireRun(UUID ocrRunId, UUID companyId) {
        return ocrRunRepository.findByIdAndCompanyId(ocrRunId, companyId)
                .orElseThrow(() -> new ApiException(DocumentErrorCode.DOCUMENT_OCR_RUN_NOT_FOUND));
    }

    private AiOcrDocumentType toAiDocumentType(DocumentType documentType) {
        return switch (documentType) {
            case PASSPORT_COPY -> AiOcrDocumentType.PASSPORT_COPY;
            case ARC -> AiOcrDocumentType.ARC;
            case CONTRACT, PERMIT -> throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_UNSUPPORTED_TYPE);
        };
    }

    private String resolveCountryCode(String nationalityCode) {
        try {
            return countryCodeResolver.fromWorkerNationalityCode(nationalityCode);
        } catch (AiRuntimeContractException exception) {
            if (exception.failureCode() == AiRuntimeFailureCode.UNSUPPORTED_OCR_COUNTRY) {
                throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_UNSUPPORTED_COUNTRY);
            }
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        String normalized = value.strip();
        if (normalized.length() < 8 || normalized.length() > 100
                || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return normalized;
    }

    private String normalizeReviewReason(DocumentOcrReviewDecision decision, String value) {
        String normalized = value == null || value.isBlank() ? null : value.strip();
        if (normalized != null && normalized.length() > 300) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (decision == DocumentOcrReviewDecision.REJECT && normalized == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return normalized;
    }

    private void requireFeatureEnabled() {
        if (!resultCipher.isAvailable()) {
            throw new ApiException(DocumentErrorCode.DOCUMENT_OCR_DISABLED);
        }
    }

    private void bindTenant(UUID companyId) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
    }

    private <T> T requiredTransaction(java.util.concurrent.Callable<T> callback) {
        T result = transactionTemplate.execute(status -> {
            try {
                return callback.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        return result;
    }

    private AiRuntimeFailureCode failureCode(RuntimeException exception) {
        if (exception instanceof AiRuntimeCallException callException) {
            return callException.failureCode();
        }
        if (exception instanceof AiRuntimeContractException contractException) {
            return contractException.failureCode();
        }
        return AiRuntimeFailureCode.TRANSPORT_FAILURE;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private void appendHumanAudit(
            DocumentOcrRun run,
            ActorContext actor,
            AuditAction action,
            RequestMetadata metadata,
            String summary
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(), run.companyId(), ActorType.HR_USER, actor.actorId(),
                effectiveRole(actor), action, AuditTargetType.DOCUMENT_OCR_RUN, run.ocrRunId(),
                metadata.requestId(), metadata.traceId(), AUDIT_EVENT_VERSION, summary, clock.instant()
        ));
    }

    private void appendSystemAudit(DocumentOcrRun run, AuditAction action, String summary) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(), run.companyId(), ActorType.AI_AGENT, null, null,
                action, AuditTargetType.DOCUMENT_OCR_RUN, run.ocrRunId(),
                run.runtimeRequestId().toString(), null, AUDIT_EVENT_VERSION, summary, clock.instant()
        ));
    }

    private void auditResultViewIfSensitive(
            DocumentOcrRun run,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        if (run.status().hasResult()) {
            appendHumanAudit(
                    run,
                    actor,
                    AuditAction.DOCUMENT_OCR_RESULT_VIEWED,
                    metadata,
                    "OCR 민감 결과 조회"
            );
        }
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

    private record Creation(DocumentOcrRun run, boolean newlyCreated) { }
    private record ExecutionInput(DocumentOcrRun run, StoredFile file) { }
}
