package com.fowoco.server.document.application.port;

import com.fowoco.server.document.domain.DocumentOcrRun;
import java.util.Optional;
import java.util.UUID;

public interface DocumentOcrRunRepository {

    void insert(DocumentOcrRun run);

    Optional<DocumentOcrRun> findByIdAndCompanyId(UUID ocrRunId, UUID companyId);

    Optional<DocumentOcrRun> findByIdempotencyKeyHashAndCompanyId(String keyHash, UUID companyId);

    Optional<DocumentOcrRun> findLatestByDocumentIdAndCompanyId(UUID documentId, UUID companyId);

    DocumentOcrRun update(DocumentOcrRun run);
}
