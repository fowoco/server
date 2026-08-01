package com.fowoco.server.workerlink.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.error.FileErrorCode;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkTenantBootstrap;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerLinkDocumentService {

    private static final String AUDIT_EVENT_VERSION = "1";

    //FileService(#13)와 동일한 값
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
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
    private final FileStorage fileStorage;
    private final AuditEventRepository auditRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public WorkerLinkDocumentService(
            WorkerLinkTenantBootstrap workerLinkTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            WorkerLinkRepository workerLinkRepository,
            WorkerLinkHasher workerLinkHasher,
            StoredFileRepository storedFileRepository,
            FileStorage fileStorage,
            AuditEventRepository auditRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.workerLinkTenantBootstrap = workerLinkTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.workerLinkRepository = workerLinkRepository;
        this.workerLinkHasher = workerLinkHasher;
        this.storedFileRepository = storedFileRepository;
        this.fileStorage = fileStorage;
        this.auditRepository = auditRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StoredFile upload(WorkerLinkDocumentUploadCommand command, RequestMetadata metadata) {
        if (command.size() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(FileErrorCode.FILE_TOO_LARGE);
        }
        if (!ALLOWED_MIME_TYPES.contains(command.mimeType())) {
            throw new ApiException(FileErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String tokenHash = workerLinkHasher.hash(command.rawToken());

        UUID companyId = workerLinkTenantBootstrap
                .findCompanyIdByWorkerLinkTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);

        WorkerLink link = workerLinkRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_FOUND));

        Instant now = clock.instant();
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_EXPIRED);
        }

        UUID storedFileId = uuidGenerator.generate();
        String storageKey = storedFileId.toString();
        String purpose = command.documentType() != null ? command.documentType() : "WORKER_LINK_SUBMISSION";

        StoredFile storedFile = StoredFile.create(
                storedFileId,
                companyId,
                command.fileName(),
                command.mimeType(),
                command.size(),
                purpose,
                link.taskId(),
                null,
                storageKey,
                now
        );

        fileStorage.store(storageKey, command.content(), command.size(), command.mimeType());
        storedFileRepository.insert(storedFile);

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
                "근로자 링크로 파일 업로드: " + purpose,
                now
        ));

        // TODO: clientRequestId를 이용한 중복 제출 방지 로직 미구현 (스키마 변경 필요, 후속 처리)

        return storedFile;
    }
}
