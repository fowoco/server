package com.fowoco.server.settings.application;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.settings.domain.CompanySettings;
import com.fowoco.server.task.domain.TaskType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class CompanySettingsAuditSummary {

    private static final int MAX_LENGTH = 500;

    private CompanySettingsAuditSummary() {
    }

    static List<String> changedFields(CompanySettings before, CompanySettings after) {
        List<String> changes = new ArrayList<>();
        add(changes, "approval_policy", before.approvalPolicy(), after.approvalPolicy(), before, after);
        add(changes, "link_expiry_hours", before.linkExpiryHours(), after.linkExpiryHours(), before, after);
        for (TaskType taskType : TaskType.values()) {
            Set<EvidenceType> beforeTypes = before.evidenceRules().getOrDefault(taskType, Set.of());
            Set<EvidenceType> afterTypes = after.evidenceRules().getOrDefault(taskType, Set.of());
            if (!beforeTypes.equals(afterTypes)) {
                changes.add(requireLength(
                        "evidence_rules." + taskType + ":"
                                + evidenceList(beforeTypes) + "->" + evidenceList(afterTypes)
                                + versionTransition(before, after)
                ));
            }
        }
        add(changes, "file_retention_days", before.fileRetentionDays(), after.fileRetentionDays(), before, after);
        add(changes, "ai_log_retention_days", before.aiLogRetentionDays(), after.aiLogRetentionDays(), before, after);
        add(changes, "audit_visibility", before.auditVisibility(), after.auditVisibility(), before, after);
        return List.copyOf(changes);
    }

    private static void add(
            List<String> changes,
            String field,
            Object beforeValue,
            Object afterValue,
            CompanySettings before,
            CompanySettings after
    ) {
        if (!beforeValue.equals(afterValue)) {
            changes.add(requireLength(
                    field + ":" + beforeValue + "->" + afterValue
                            + versionTransition(before, after)
            ));
        }
    }

    private static String versionTransition(CompanySettings before, CompanySettings after) {
        return ",version:" + before.version() + "->" + after.version();
    }

    private static String evidenceList(Set<EvidenceType> evidenceTypes) {
        if (evidenceTypes.isEmpty()) {
            return "[]";
        }
        EnumSet<EvidenceType> sorted = EnumSet.copyOf(evidenceTypes);
        return "[" + sorted.stream().map(Enum::name).reduce((left, right) -> left + "," + right)
                .orElseThrow() + "]";
    }

    private static String requireLength(String summary) {
        if (summary.length() > MAX_LENGTH) {
            throw new IllegalStateException("Company settings audit summary exceeds 500 characters");
        }
        return summary;
    }
}
