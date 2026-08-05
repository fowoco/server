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

    private final StoredFileRepository storedFileRepository;
    private final FileStorage fileStorage;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final AuditEventRepository auditRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public FileService(
            StoredFileRepository storedFileRepository,
            FileStorage fileStorage,
            TaskRepository taskRepository,
            WorkerRepository workerRepository,
            AuditEventRepository auditRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.storedFileRepository = storedFileRepository;
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
        if (!ALLOWED_MIME_TYPES.contains(command.mimeType())) {
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

        fileStorage.store(storageKey, command.content(), command.size(), command.mimeType());
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
}
