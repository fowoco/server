package com.fowoco.server.file.application;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.file.application.error.FileErrorCode;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileService {

    /**
     * 확정된 기준 없음. 20으로 시작하고,
     * 실제 사용 파일(신분증 사진, 계약서 PDF 등) 확인되면 조정
     */
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final StoredFileRepository storedFileRepository;
    private final FileStorage fileStorage;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public FileService(
            StoredFileRepository storedFileRepository,
            FileStorage fileStorage,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.storedFileRepository = storedFileRepository;
        this.fileStorage = fileStorage;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StoredFile upload(FileCreateCommand command) {
        if (command.size() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(FileErrorCode.FILE_TOO_LARGE);
        }
        if (!ALLOWED_MIME_TYPES.contains(command.mimeType())) {
            throw new ApiException(FileErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        UUID storedFileId = uuidGenerator.generate();
        String storageKey = storedFileId.toString();

        StoredFile storedFile = StoredFile.create(
                storedFileId,
                command.companyId(),
                command.name(),
                command.mimeType(),
                command.size(),
                command.purpose(),
                command.taskId(),
                command.workerId(),
                storageKey,
                clock.instant()
        );

        fileStorage.store(storageKey, command.content(), command.size(), command.mimeType());
        storedFileRepository.insert(storedFile);
        return storedFile;
    }
}
