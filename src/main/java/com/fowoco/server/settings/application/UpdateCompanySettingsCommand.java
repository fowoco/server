package com.fowoco.server.settings.application;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.settings.domain.ApprovalPolicy;
import com.fowoco.server.settings.domain.AuditVisibility;
import com.fowoco.server.task.domain.TaskType;
import java.util.Map;
import java.util.Set;

public record UpdateCompanySettingsCommand(
        long expectedVersion,
        PatchField<ApprovalPolicy> approvalPolicy,
        PatchField<Long> linkExpiryHours,
        PatchField<Map<TaskType, Set<EvidenceType>>> evidenceRules,
        PatchField<Integer> fileRetentionDays,
        PatchField<Integer> aiLogRetentionDays,
        PatchField<AuditVisibility> auditVisibility
) {
}
