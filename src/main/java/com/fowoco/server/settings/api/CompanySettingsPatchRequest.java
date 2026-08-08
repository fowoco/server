package com.fowoco.server.settings.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.settings.application.PatchField;
import com.fowoco.server.settings.application.UpdateCompanySettingsCommand;
import com.fowoco.server.settings.domain.ApprovalPolicy;
import com.fowoco.server.settings.domain.AuditVisibility;
import com.fowoco.server.settings.domain.CompanySettings;
import com.fowoco.server.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;
import java.util.Set;

@Schema(
        name = "CompanySettingsPatchRequest",
        description = "생략한 설정은 유지하며, 명시적 null과 정의되지 않은 필드는 거부합니다.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompanySettingsPatchRequest {

    @NotNull
    @PositiveOrZero
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", example = "4")
    private Long expectedVersion;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
    private ApprovalPolicy approvalPolicy;
    @JsonIgnore
    @Schema(hidden = true)
    private boolean approvalPolicyPresent;

    @Min(CompanySettings.MIN_LINK_EXPIRY_HOURS)
    @Max(CompanySettings.MAX_LINK_EXPIRY_HOURS)
    @Schema(minimum = "1", maximum = "168", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long linkExpiryHours;
    @JsonIgnore
    @Schema(hidden = true)
    private boolean linkExpiryHoursPresent;

    @Valid
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
    private Map<@NotNull TaskType, @NotNull Set<@NotNull EvidenceType>> evidenceRules;
    @JsonIgnore
    @Schema(hidden = true)
    private boolean evidenceRulesPresent;

    @Min(CompanySettings.MIN_FILE_RETENTION_DAYS)
    @Max(CompanySettings.MAX_FILE_RETENTION_DAYS)
    @Schema(minimum = "30", maximum = "3650", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer fileRetentionDays;
    @JsonIgnore
    @Schema(hidden = true)
    private boolean fileRetentionDaysPresent;

    @Min(CompanySettings.MIN_AI_LOG_RETENTION_DAYS)
    @Max(CompanySettings.MAX_AI_LOG_RETENTION_DAYS)
    @Schema(minimum = "7", maximum = "365", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer aiLogRetentionDays;
    @JsonIgnore
    @Schema(hidden = true)
    private boolean aiLogRetentionDaysPresent;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
    private AuditVisibility auditVisibility;
    @JsonIgnore
    @Schema(hidden = true)
    private boolean auditVisibilityPresent;

    @JsonSetter(value = "expected_version", nulls = Nulls.FAIL)
    @JsonProperty(value = "expected_version", required = true)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", example = "4")
    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    @JsonSetter(value = "approval_policy", nulls = Nulls.FAIL)
    @JsonProperty("approval_policy")
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
    public void setApprovalPolicy(ApprovalPolicy approvalPolicy) {
        this.approvalPolicyPresent = true;
        this.approvalPolicy = approvalPolicy;
    }

    @JsonSetter(value = "link_expiry_hours", nulls = Nulls.FAIL)
    @JsonProperty("link_expiry_hours")
    @Schema(minimum = "1", maximum = "168", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    public void setLinkExpiryHours(Long linkExpiryHours) {
        this.linkExpiryHoursPresent = true;
        this.linkExpiryHours = linkExpiryHours;
    }

    @JsonSetter(value = "evidence_rules", nulls = Nulls.FAIL)
    @JsonProperty("evidence_rules")
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
    public void setEvidenceRules(Map<TaskType, Set<EvidenceType>> evidenceRules) {
        this.evidenceRulesPresent = true;
        this.evidenceRules = evidenceRules;
    }

    @JsonSetter(value = "file_retention_days", nulls = Nulls.FAIL)
    @JsonProperty("file_retention_days")
    @Schema(minimum = "30", maximum = "3650", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    public void setFileRetentionDays(Integer fileRetentionDays) {
        this.fileRetentionDaysPresent = true;
        this.fileRetentionDays = fileRetentionDays;
    }

    @JsonSetter(value = "ai_log_retention_days", nulls = Nulls.FAIL)
    @JsonProperty("ai_log_retention_days")
    @Schema(minimum = "7", maximum = "365", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    public void setAiLogRetentionDays(Integer aiLogRetentionDays) {
        this.aiLogRetentionDaysPresent = true;
        this.aiLogRetentionDays = aiLogRetentionDays;
    }

    @JsonSetter(value = "audit_visibility", nulls = Nulls.FAIL)
    @JsonProperty("audit_visibility")
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
    public void setAuditVisibility(AuditVisibility auditVisibility) {
        this.auditVisibilityPresent = true;
        this.auditVisibility = auditVisibility;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unknown company settings field: " + fieldName);
    }

    @JsonIgnore
    public UpdateCompanySettingsCommand toCommand() {
        return new UpdateCompanySettingsCommand(
                expectedVersion,
                field(approvalPolicyPresent, approvalPolicy),
                field(linkExpiryHoursPresent, linkExpiryHours),
                field(evidenceRulesPresent, evidenceRules),
                field(fileRetentionDaysPresent, fileRetentionDays),
                field(aiLogRetentionDaysPresent, aiLogRetentionDays),
                field(auditVisibilityPresent, auditVisibility)
        );
    }

    private <T> PatchField<T> field(boolean present, T value) {
        return present ? PatchField.of(value) : PatchField.absent();
    }
}
