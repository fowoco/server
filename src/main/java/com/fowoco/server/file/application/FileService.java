package com.fowoco.server.file.application;

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
import com.fowoco.server.file.application.error.FileErrorCode;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.application.validation.HwpSignatureValidator;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.worker.application.error.WorkerErrorCode;
import com.fowoco.server.worker.application.port.WorkerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileService {

    private static final String AUDIT_EVENT_VERSION = "1";

    /**
     * 확정된 기준 없음. 20으로 시작하고,
     * 실제 사용 파일(신분증 사진, 계약서 PDF 등) 확인되면 조정
     */
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
            "application/hwp+zip"
    );
    private static final String HWP_EXTENSION = ".hwp";

    private final StoredFileRepository storedFileRepository;
    private final HwpSignatureValidator hwpSignatureValidator;
    private final FileStorage fileStorage;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final AuditEventRepository auditRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public FileService(
            StoredFileRepository storedFileRepository,
            HwpSignatureValidator hwpSignatureValidator,
            FileStorage fileStorage,
            TaskRepository taskRepository,
            WorkerRepository workerRepository,
            AuditEventRepository auditRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.storedFileRepository = storedFileRepository;
        this.hwpSignatureValidator = hwpSignatureValidator;
        this.fileStorage = fileStorage;
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.auditRepository = auditRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StoredFile upload(FileCreateCommand command, ActorContext actor, RequestMetadata metadata) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        if (command.size() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(FileErrorCode.FILE_TOO_LARGE);
        }
        byte[] contentBytes = readAllBytes(command.content());
        if (isHwpExtension(command.name())) {
            if (!hwpSignatureValidator.isValidHwp(contentBytes)) {
                throw new ApiException(FileErrorCode.UNSUPPORTED_FILE_TYPE);
            }
        } else if (!ALLOWED_MIME_TYPES.contains(command.mimeType())) {
            throw new ApiException(FileErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (command.taskId() != null) {
            taskRepository.findByIdAndCompanyId(command.taskId(), companyId)
                    .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
        }
        if (command.workerId() != null) {
            workerRepository.findByWorkerIdAndCompanyId(command.workerId(), companyId)
                    .orElseThrow(() -> new ApiException(WorkerErrorCode.WORKER_NOT_FOUND));
        }

        UUID storedFileId = uuidGenerator.generate();
        String storageKey = storedFileId.toString();
        Instant now = clock.instant();

        StoredFile storedFile = StoredFile.create(
                storedFileId,
                companyId,
                command.name(),
                command.mimeType(),
                command.size(),
                command.purpose(),
                command.taskId(),
                command.workerId(),
                storageKey,
                now
        );

        fileStorage.store(storageKey, new java.io.ByteArrayInputStream(contentBytes), command.size(), command.mimeType());
        storedFileRepository.insert(storedFile);

        appendAudit(
                actor,
                AuditAction.FILE_UPLOADED,
                AuditTargetType.FILE,
                storedFileId,
                "파일 업로드: " + command.purpose(),
                metadata,
                now
        );

        return storedFile;
    }

    @Transactional
    public FileDownloadResult download(UUID storedFileId, ActorContext actor, RequestMetadata metadata) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        StoredFile storedFile = storedFileRepository.findByIdAndCompanyId(storedFileId, actor.companyId())
                .orElseThrow(() -> new ApiException(FileErrorCode.FILE_NOT_FOUND));
        java.io.InputStream content = fileStorage.open(storedFile.storageKey())
                .orElseThrow(() -> new ApiException(FileErrorCode.FILE_NOT_FOUND));

        appendAudit(
                actor,
                AuditAction.FILE_DOWNLOADED,
                AuditTargetType.FILE,
                storedFileId,
                "파일 다운로드",
                metadata,
                clock.instant()
        );
        return new FileDownloadResult(storedFile, content);
    }

    private void appendAudit(
            ActorContext actor,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String summary,
            RequestMetadata metadata,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                effectiveRole(actor),
                action,
                targetType,
                targetId,
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                summary,
                now
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

    private boolean isHwpExtension(String name) {
        return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(HWP_EXTENSION);
    }

    private byte[] readAllBytes(java.io.InputStream content) {
        try {
            return content.readAllBytes();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("파일 내용을 읽을 수 없습니다.", exception);
        }
    }
}
