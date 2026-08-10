package com.fowoco.server.settings.infrastructure.persistence;

import com.fowoco.server.settings.domain.ApprovalPolicy;
import com.fowoco.server.settings.domain.AuditVisibility;
import com.fowoco.server.settings.domain.CompanySettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "company_settings")
class CompanySettingsJpaEntity {

    @Id
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_policy", nullable = false, length = 30)
    private ApprovalPolicy approvalPolicy;

    @Column(name = "link_expiry_hours", nullable = false)
    private long linkExpiryHours;

    @Column(name = "evidence_rules_json", nullable = false, columnDefinition = "TEXT")
    private String evidenceRulesJson;

    @Column(name = "file_retention_days", nullable = false)
    private int fileRetentionDays;

    @Column(name = "ai_log_retention_days", nullable = false)
    private int aiLogRetentionDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_visibility", nullable = false, length = 30)
    private AuditVisibility auditVisibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CompanySettingsJpaEntity() {
    }

    private CompanySettingsJpaEntity(
            UUID companyId,
            ApprovalPolicy approvalPolicy,
            long linkExpiryHours,
            String evidenceRulesJson,
            int fileRetentionDays,
            int aiLogRetentionDays,
            AuditVisibility auditVisibility,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.companyId = companyId;
        this.approvalPolicy = approvalPolicy;
        this.linkExpiryHours = linkExpiryHours;
        this.evidenceRulesJson = evidenceRulesJson;
        this.fileRetentionDays = fileRetentionDays;
        this.aiLogRetentionDays = aiLogRetentionDays;
        this.auditVisibility = auditVisibility;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    static CompanySettingsJpaEntity fromDomain(
            CompanySettings companySettings,
            CompanySettingsJsonCodec jsonCodec
    ) {
        Objects.requireNonNull(companySettings, "companySettings must not be null");
        Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        return new CompanySettingsJpaEntity(
                companySettings.companyId(),
                companySettings.approvalPolicy(),
                companySettings.linkExpiryHours(),
                jsonCodec.encodeEvidenceRules(companySettings.evidenceRules()),
                companySettings.fileRetentionDays(),
                companySettings.aiLogRetentionDays(),
                companySettings.auditVisibility(),
                companySettings.createdAt(),
                companySettings.updatedAt(),
                companySettings.version()
        );
    }

    CompanySettings toDomain(CompanySettingsJsonCodec jsonCodec) {
        return new CompanySettings(
                companyId,
                approvalPolicy,
                linkExpiryHours,
                jsonCodec.decodeEvidenceRules(evidenceRulesJson),
                fileRetentionDays,
                aiLogRetentionDays,
                auditVisibility,
                createdAt,
                updatedAt,
                version
        );
    }
}
