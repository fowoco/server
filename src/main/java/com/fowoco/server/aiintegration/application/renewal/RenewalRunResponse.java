package com.fowoco.server.aiintegration.application.renewal;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RenewalRunResponse(
        UUID requestId,
        UUID attemptId,
        UUID taskId,
        String intent,
        String workflowId,
        BigDecimal confidence,
        String status,
        String outcome,
        String scenario,
        String phase,
        String step,
        Map<String, Object> slots,
        List<String> missingSlots,
        List<RenewalRequestedField> requestedFields,
        String guideMessage,
        String workerRequestMessage,
        Map<String, Object> languageAssistant,
        Map<String, Object> ocrResult,
        List<Map<String, Object>> generatedDocuments,
        List<Map<String, String>> evidence,
        Map<String, Object> documentValidation,
        List<String> caseSignals,
        List<Map<String, Object>> progressEvents,
        String supervisorReason,
        String supervisorSource,
        String activeSubgraph,
        List<String> errors
) {
    public RenewalRunResponse {
        slots = copyMap(slots);
        missingSlots = copyList(missingSlots);
        requestedFields = requestedFields == null ? List.of() : List.copyOf(requestedFields);
        languageAssistant = copyNullableMap(languageAssistant);
        ocrResult = copyNullableMap(ocrResult);
        generatedDocuments = copyMapList(generatedDocuments);
        evidence = evidence == null
                ? List.of()
                : evidence.stream().map(RenewalRunResponse::copyStringMap).toList();
        documentValidation = copyNullableMap(documentValidation);
        caseSignals = copyList(caseSignals);
        progressEvents = copyMapList(progressEvents);
        errors = copyList(errors);
    }

    private static Map<String, Object> copyMap(Map<String, Object> value) {
        return value == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static Map<String, Object> copyNullableMap(Map<String, Object> value) {
        return value == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static List<String> copyList(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    private static List<Map<String, Object>> copyMapList(List<Map<String, Object>> value) {
        return value == null ? List.of() : value.stream().map(RenewalRunResponse::copyMap).toList();
    }

    private static Map<String, String> copyStringMap(Map<String, String> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
