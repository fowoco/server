package com.fowoco.server.settings.application;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.settings.domain.CompanySettings;
import com.fowoco.server.task.domain.TaskType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CompanySettingsAuditSummary {

    private static final int MAX_LENGTH = 500;

    private CompanySettingsAuditSummary() {
    }

    static String changedFields(CompanySettings before, CompanySettings after) {
        List<String> changes = new ArrayList<>();
        add(changes, "approval_policy", before.approvalPolicy(), after.approvalPolicy());
        add(changes, "link_expiry_hours", before.linkExpiryHours(), after.linkExpiryHours());
        if (!before.evidenceRules().equals(after.evidenceRules())) {
            changes.add(jsonPair(
                    "evidence_rules",
                    encodeEvidenceRules(before.evidenceRules()),
                    encodeEvidenceRules(after.evidenceRules())
            ));
        }
        add(changes, "file_retention_days", before.fileRetentionDays(), after.fileRetentionDays());
        add(changes, "ai_log_retention_days", before.aiLogRetentionDays(), after.aiLogRetentionDays());
        add(changes, "audit_visibility", before.auditVisibility(), after.auditVisibility());
        String summary = "{" + String.join(",", changes) + "}";
        if (summary.length() > MAX_LENGTH) {
            throw new IllegalStateException("Company settings audit summary exceeds 500 characters");
        }
        return summary;
    }

    private static void add(List<String> changes, String field, Object before, Object after) {
        if (!before.equals(after)) {
            changes.add(jsonPair(field, before, after));
        }
    }

    private static String jsonPair(String field, Object before, Object after) {
        return "\"" + field + "\":[" + jsonValue(before) + "," + jsonValue(after) + "]";
    }

    private static String jsonValue(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        return "\"" + value + "\"";
    }

    private static String encodeEvidenceRules(Map<TaskType, Set<EvidenceType>> rules) {
        if (rules.isEmpty()) {
            return "-";
        }
        List<String> entries = new ArrayList<>();
        rules.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(
                        taskCode(entry.getKey()) + "=" + evidenceCodes(entry.getValue())
                ));
        return String.join(";", entries);
    }

    private static String evidenceCodes(Set<EvidenceType> evidenceTypes) {
        return evidenceTypes.stream()
                .sorted()
                .map(CompanySettingsAuditSummary::evidenceCode)
                .reduce("", String::concat);
    }

    private static String taskCode(TaskType taskType) {
        return switch (taskType) {
            case RECONTRACT -> "REC";
            case EMPLOYMENT_PERIOD_EXTENSION -> "EPE";
            case STAY_PERIOD_EXTENSION -> "SPE";
            case DOCUMENT_REQUEST -> "DR";
            case WORKER_ONBOARDING -> "WO";
            case PAYROLL_EXPLANATION -> "PE";
            case EMPLOYMENT_CHANGE -> "EC";
            case WORK_INSTRUCTION -> "WI";
        };
    }

    private static String evidenceCode(EvidenceType evidenceType) {
        return switch (evidenceType) {
            case DOCUMENT -> "D";
            case RECEIPT -> "R";
            case OFFICIAL_RESULT -> "O";
            case HR_CONFIRMATION -> "H";
        };
    }
}
