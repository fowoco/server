package com.fowoco.server.workerlink.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.FileStorageRollbackCompensation;
import com.fowoco.server.file.application.error.FileErrorCode;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerDocumentUploadIdempotencyRecord;
import com.fowoco.server.workerlink.application.port.WorkerDocumentUploadIdempotencyRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkTenantBootstrap;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerLinkDocumentService {

    private static final String AUDIT_EVENT_VERSION = "1";
    private static final String DEFAULT_DOCUMENT_PURPOSE = "WORKER_LINK_SUBMISSION";
    private static final String REQUEST_HASH_VERSION = "worker-link-document-upload:v1";
    private static final String ROLLBACK_ACTION = "worker_link_document_upload";
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._:-]*$"
    );
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final WorkerLinkTenantBootstrap workerLinkTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final WorkerLinkRepository workerLinkRepository;
    private final WorkerLinkHasher workerLinkHasher;
    private final StoredFileRepository storedFileRepository;
    private final WorkerDocumentUploadIdempotencyRepository uploadIdempotencyRepository;
    private final FileStorage fileStorage;
    private final FileStorageRollbackCompensation rollbackCompensation;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkDocumentService(
            WorkerLinkTenantBootstrap workerLinkTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerLinkRepository workerLinkRepository,
            WorkerLinkHasher workerLinkHasher,
            StoredFileRepository storedFileRepository,
            WorkerDocumentUploadIdempotencyRepository uploadIdempotencyRepository,
            FileStorage fileStorage,
            FileStorageRollbackCompensation rollbackCompensation,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerLinkTenantBootstrap = workerLinkTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerLinkRepository = workerLinkRepository;
        this.workerLinkHasher = workerLinkHasher;
        this.storedFileRepository = storedFileRepository;
        this.uploadIdempotencyRepository = uploadIdempotencyRepository;
        this.fileStorage = fileStorage;
        this.rollbackCompensation = rollbackCompensation;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public WorkerLinkDocumentUploadResult upload(WorkerLinkDocumentUploadCommand command, RequestMetadata metadata) {
        NormalizedUploadRequest request = normalize(command);
        String tokenHash = workerLinkHasher.hash(command.rawToken());
        String idempotencyKeyHash = workerLinkHasher.hash(request.idempotencyKey());

        UUID companyId = workerLinkTenantBootstrap
                .findCompanyIdByWorkerLinkTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);

        WorkerLink locatedLink = workerLinkRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));
        WorkerLink link = workerLinkRepository
                .findByIdAndCompanyIdForUpdate(locatedLink.workerLinkId(), companyId)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        Instant now = clock.instant();
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND);
        }

        Optional<WorkerDocumentUploadIdempotencyRecord> existing = uploadIdempotencyRepository
                .findByKeyHash(link.workerLinkId(), companyId, idempotencyKeyHash);
        if (existing.isPresent()) {
            return replayExistingUpload(command, request, link, companyId, existing.get());
        }

        UUID storedFileId = uuidGenerator.generate();
        String storageKey = storedFileId.toString();
        StoredFile verifiedFile = StoredFile.create(
                storedFileId,
                companyId,
                request.fileName(),
                request.mimeType(),
                request.size(),
                request.purpose(),
                link.taskId(),
                null,
                storageKey,
                now
        ).verify();

        FileStorageRollbackCompensation.Registration rollbackRegistration =
                rollbackCompensation.register(storageKey, metadata, ROLLBACK_ACTION);
        String contentChecksum = storeAndChecksum(storageKey, command.content(), request, rollbackRegistration);
        String requestHash = calculateRequestHash(request, contentChecksum);

        storedFileRepository.insert(verifiedFile);
        uploadIdempotencyRepository.save(
                link.workerLinkId(),
                companyId,
                idempotencyKeyHash,
                requestHash,
                storedFileId
        );

        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                companyId,
                ActorType.WORKER_LINK,
                null,
                null,
                AuditAction.FILE_UPLOADED,
                AuditTargetType.FILE,
                storedFileId,
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "근로자 링크로 파일 업로드: " + request.purpose(),
                now
        ));

        return new WorkerLinkDocumentUploadResult(verifiedFile, link.expiresAt());
    }

    private WorkerLinkDocumentUploadResult replayExistingUpload(
            WorkerLinkDocumentUploadCommand command,
            NormalizedUploadRequest request,
            WorkerLink link,
            UUID companyId,
            WorkerDocumentUploadIdempotencyRecord existing
    ) {
        String contentChecksum = checksumAndDiscard(command.content(), request.size());
        String requestHash = calculateRequestHash(request, contentChecksum);
        if (!existing.requestHash().equals(requestHash)) {
            throw new ApiException(WorkerLinkErrorCode.IDEMPOTENCY_CONFLICT);
        }

        StoredFile existingFile = storedFileRepository.findByIdAndCompanyId(existing.storedFileId(), companyId)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.UPLOAD_NOT_AVAILABLE));
        return new WorkerLinkDocumentUploadResult(existingFile, link.expiresAt());
    }

    private NormalizedUploadRequest normalize(WorkerLinkDocumentUploadCommand command) {
        if (command.size() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "파일 크기는 0보다 커야 합니다.");
        }
        if (command.size() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(FileErrorCode.FILE_TOO_LARGE);
        }

        String fileName = normalizeRequiredText(command.fileName(), "파일명이 필요합니다.");
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "파일명은 255자 이하여야 합니다.");
        }

        String mimeType = normalizeRequiredText(command.mimeType(), "파일 MIME 유형이 필요합니다.")
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new ApiException(FileErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String purpose = normalizePurpose(command.documentType());
        String idempotencyKey = normalizeRequiredText(
                command.idempotencyKey(),
                "Idempotency-Key 헤더가 필요합니다."
        );
        if (idempotencyKey.length() < MIN_IDEMPOTENCY_KEY_LENGTH
                || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key는 8~100자의 영문, 숫자, 점, 밑줄, 콜론 또는 하이픈이어야 합니다."
            );
        }

        return new NormalizedUploadRequest(
                fileName,
                mimeType,
                command.size(),
                purpose,
                idempotencyKey
        );
    }

    private String normalizePurpose(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return DEFAULT_DOCUMENT_PURPOSE;
        }
        String normalized = documentType.strip().toUpperCase(Locale.ROOT);
        try {
            return DocumentType.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 문서 유형입니다.");
        }
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.strip();
    }

    private String storeAndChecksum(
            String storageKey,
            InputStream content,
            NormalizedUploadRequest request,
            FileStorageRollbackCompensation.Registration rollbackRegistration
    ) {
        MessageDigest digest = newSha256Digest();
        CountingInputStream countingContent = new CountingInputStream(content);
        try (DigestInputStream digestContent = new DigestInputStream(countingContent, digest)) {
            fileStorage.store(storageKey, digestContent, request.size(), request.mimeType());
            rollbackRegistration.markCreated();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to close uploaded file", exception);
        }
        requireActualSize(countingContent.count(), request.size());
        return HexFormat.of().formatHex(digest.digest());
    }

    private String checksumAndDiscard(InputStream content, long expectedSize) {
        MessageDigest digest = newSha256Digest();
        CountingInputStream countingContent = new CountingInputStream(content);
        try (DigestInputStream digestContent = new DigestInputStream(countingContent, digest)) {
            digestContent.transferTo(OutputStream.nullOutputStream());
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded file", exception);
        }
        requireActualSize(countingContent.count(), expectedSize);
        return HexFormat.of().formatHex(digest.digest());
    }

    private void requireActualSize(long actualSize, long expectedSize) {
        if (actualSize != expectedSize) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "선언된 파일 크기와 실제 파일 크기가 일치하지 않습니다."
            );
        }
    }

    private String calculateRequestHash(NormalizedUploadRequest request, String contentChecksum) {
        MessageDigest digest = newSha256Digest();
        updateDigest(digest, REQUEST_HASH_VERSION);
        updateDigest(digest, request.purpose());
        updateDigest(digest, request.fileName());
        updateDigest(digest, request.mimeType());
        updateDigest(digest, Long.toString(request.size()));
        updateDigest(digest, contentChecksum);
        return HexFormat.of().formatHex(digest.digest());
    }

    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record NormalizedUploadRequest(
            String fileName,
            String mimeType,
            long size,
            String purpose,
            String idempotencyKey
    ) {
    }

    private static final class CountingInputStream extends FilterInputStream {

        private long count;

        private CountingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int bytesRead = in.read(bytes, offset, length);
            if (bytesRead > 0) {
                count += bytesRead;
            }
            return bytesRead;
        }

        private long count() {
            return count;
        }
    }
}
