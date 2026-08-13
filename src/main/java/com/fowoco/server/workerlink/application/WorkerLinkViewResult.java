package com.fowoco.server.workerlink.application;

import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.time.LocalDate;
import java.util.List;

public record WorkerLinkViewResult(
        String guidance,
        String language,
        LocalDate dueDate,
        List<DocumentType> requestedDocumentTypes,
        List<WorkerResponseType> allowedResponses,
        List<WorkerRequestedAction> requestedActions
) {
}
