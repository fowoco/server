package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.task.domain.Task;
import java.util.List;

public record RenewalExecutionResult(
        Task task,
        RenewalRunResponse agentResult,
        List<GeneratedDocumentResult> generatedDocuments,
        DocumentRequestDraft workerMessageDraft
) {
    public RenewalExecutionResult {
        generatedDocuments = generatedDocuments == null ? List.of() : List.copyOf(generatedDocuments);
    }
}
