package com.fowoco.server.settings.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.settings.domain.ApprovalPolicy;
import com.fowoco.server.settings.domain.AuditVisibility;
import com.fowoco.server.settings.domain.CompanySettings;
import com.fowoco.server.task.domain.TaskType;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanySettingsAuditSummaryTest {

    @Test
    void worstCaseEvidenceDiffRetainsBeforeAndAfterWithinDatabaseLimit() {
        Map<TaskType, Set<EvidenceType>> allEvidence = new EnumMap<>(TaskType.class);
        for (TaskType taskType : TaskType.values()) {
            allEvidence.put(taskType, EnumSet.allOf(EvidenceType.class));
        }
        CompanySettings before = settings(Map.of());
        CompanySettings after = settings(allEvidence);

        var summaries = CompanySettingsAuditSummary.changedFields(before, after);

        assertThat(summaries).hasSize(TaskType.values().length);
        assertThat(summaries).contains(
                "evidence_rules.RECONTRACT:[]->[DOCUMENT,RECEIPT,OFFICIAL_RESULT,HR_CONFIRMATION],version:0->0",
                "evidence_rules.STAY_PERIOD_EXTENSION:[]->[DOCUMENT,RECEIPT,OFFICIAL_RESULT,HR_CONFIRMATION],version:0->0",
                "evidence_rules.WORK_INSTRUCTION:[]->[DOCUMENT,RECEIPT,OFFICIAL_RESULT,HR_CONFIRMATION],version:0->0"
        );
        assertThat(summaries).allSatisfy(summary ->
                assertThat(summary.length()).isLessThanOrEqualTo(500)
        );
    }

    private CompanySettings settings(Map<TaskType, Set<EvidenceType>> evidenceRules) {
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        return new CompanySettings(
                UUID.fromString("61000000-0000-0000-0000-000000000001"),
                ApprovalPolicy.ADMIN_OR_HR,
                72,
                evidenceRules,
                365,
                90,
                AuditVisibility.ADMIN_ONLY,
                now,
                now,
                0
        );
    }
}
