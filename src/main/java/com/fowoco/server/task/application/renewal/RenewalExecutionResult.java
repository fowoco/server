package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.document.domain.DocumentRequestDraft;
import com.fowoco.server.task.domain.Task;

public record RenewalExecutionResult(
        Task task,
        RenewalRunResponse agentResult,
        DocumentRequestDraft workerMessageDraft
) {
}
