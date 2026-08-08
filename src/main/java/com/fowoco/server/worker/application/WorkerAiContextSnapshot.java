package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.DocumentType;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable Worker data exposed to the AiRun application boundary.
 */
public record WorkerAiContextSnapshot(
        UUID workerId,
        UUID companyId,
        String displayName,
        String nationalityCode,
        String preferredLanguage,
        String workStatus,
        LocalDate stayExpiryDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        Map<DocumentType, WorkerDocumentAiContextSnapshot> documents
) {

    public WorkerAiContextSnapshot {
        Objects.requireNonNull(workerId, "workerId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(workStatus, "workStatus must not be null");
        Objects.requireNonNull(documents, "documents must not be null");
        documents = Map.copyOf(documents);
    }
}
