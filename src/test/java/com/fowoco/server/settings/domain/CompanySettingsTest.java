package com.fowoco.server.settings.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.task.domain.TaskType;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanySettingsTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void defaultsMatchTheFrozenMvpContract() {
        CompanySettings settings = CompanySettings.defaults(COMPANY_ID, NOW);

        assertThat(settings.companyId()).isEqualTo(COMPANY_ID);
        assertThat(settings.approvalPolicy()).isEqualTo(ApprovalPolicy.ADMIN_OR_HR);
        assertThat(settings.linkExpiryHours()).isEqualTo(72L);
        assertThat(settings.evidenceRules()).isEmpty();
        assertThat(settings.fileRetentionDays()).isEqualTo(365);
        assertThat(settings.aiLogRetentionDays()).isEqualTo(90);
        assertThat(settings.auditVisibility()).isEqualTo(AuditVisibility.ADMIN_ONLY);
        assertThat(settings.createdAt()).isEqualTo(NOW);
        assertThat(settings.updatedAt()).isEqualTo(NOW);
        assertThat(settings.version()).isZero();
    }

    @Test
    void validatesEveryFrozenNumericRange() {
        assertThatThrownBy(() -> settings(0, 365, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkExpiryHours");
        assertThatThrownBy(() -> settings(169, 365, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkExpiryHours");
        assertThatThrownBy(() -> settings(72, 29, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileRetentionDays");
        assertThatThrownBy(() -> settings(72, 3651, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileRetentionDays");
        assertThatThrownBy(() -> settings(72, 365, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiLogRetentionDays");
        assertThatThrownBy(() -> settings(72, 365, 366))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiLogRetentionDays");
    }

    @Test
    void evidenceRulesAreDefensivelyImmutable() {
        EnumMap<TaskType, Set<EvidenceType>> rules = new EnumMap<>(TaskType.class);
        EnumSet<EvidenceType> required = EnumSet.of(EvidenceType.DOCUMENT);
        rules.put(TaskType.DOCUMENT_REQUEST, required);

        CompanySettings settings = new CompanySettings(
                COMPANY_ID,
                ApprovalPolicy.ADMIN_OR_HR,
                72,
                rules,
                365,
                90,
                AuditVisibility.ADMIN_ONLY,
                NOW,
                NOW,
                0
        );
        required.add(EvidenceType.RECEIPT);
        rules.clear();

        assertThat(settings.evidenceRules())
                .containsOnlyKeys(TaskType.DOCUMENT_REQUEST);
        assertThat(settings.evidenceRules().get(TaskType.DOCUMENT_REQUEST))
                .containsExactly(EvidenceType.DOCUMENT);
        assertThatThrownBy(() -> settings.evidenceRules().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private CompanySettings settings(long linkHours, int fileDays, int aiDays) {
        return new CompanySettings(
                COMPANY_ID,
                ApprovalPolicy.ADMIN_OR_HR,
                linkHours,
                Map.of(),
                fileDays,
                aiDays,
                AuditVisibility.ADMIN_ONLY,
                NOW,
                NOW,
                0
        );
    }
}
