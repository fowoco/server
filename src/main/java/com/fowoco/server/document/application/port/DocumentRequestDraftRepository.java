package com.fowoco.server.document.application.port;

import com.fowoco.server.document.domain.DocumentRequestDraft;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRequestDraftRepository {

    void insert(DocumentRequestDraft draft);

    Optional<DocumentRequestDraft> findByTaskIdAndCompanyId(UUID taskId, UUID companyId);

    DocumentRequestDraft update(DocumentRequestDraft draft);
}
