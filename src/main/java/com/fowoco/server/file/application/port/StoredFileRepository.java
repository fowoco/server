package com.fowoco.server.file.application.port;

import com.fowoco.server.file.domain.StoredFile;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository {

    void insert(StoredFile storedFile);

    Optional<StoredFile> findByIdAndCompanyId(UUID storedFileId, UUID companyId);
}
