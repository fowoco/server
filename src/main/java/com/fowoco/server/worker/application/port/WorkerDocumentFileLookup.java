package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.Optional;
import java.util.UUID;

public interface WorkerDocumentFileLookup {

    Optional<WorkerDocument> findByFileIdAndCompanyId(UUID fileId, UUID companyId);
}
