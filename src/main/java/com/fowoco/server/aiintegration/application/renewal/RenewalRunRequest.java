package com.fowoco.server.aiintegration.application.renewal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RenewalRunRequest(
        UUID requestId,
        UUID attemptId,
        String instruction,
        UUID workerId,
        UUID companyId,
        UUID taskId,
        Map<String, Object> slots,
        List<RenewalDocumentInput> documents,
        Map<String, Object> ocrResult,
        RenewalWorkerSnapshot worker,
        RenewalCompanySnapshot company,
        RenewalTaskSnapshot task,
        RenewalAgentMode agentMode
) {
    public RenewalRunRequest {
        slots = immutableMap(slots);
        documents = documents == null ? List.of() : List.copyOf(documents);
        ocrResult = ocrResult == null ? null : immutableMap(ocrResult);
        agentMode = agentMode == null ? RenewalAgentMode.LEGACY : agentMode;
    }

    public RenewalRunRequest(
            UUID requestId,
            UUID attemptId,
            String instruction,
            UUID workerId,
            UUID companyId,
            UUID taskId,
            Map<String, Object> slots,
            List<RenewalDocumentInput> documents,
            Map<String, Object> ocrResult,
            RenewalWorkerSnapshot worker,
            RenewalCompanySnapshot company,
            RenewalTaskSnapshot task
    ) {
        this(
                requestId, attemptId, instruction, workerId, companyId, taskId,
                slots, documents, ocrResult, worker, company, task, RenewalAgentMode.LEGACY
        );
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
