package com.fowoco.server.settings.domain;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.task.domain.TaskType;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CompanySettings {

    public static final ApprovalPolicy DEFAULT_APPROVAL_POLICY = ApprovalPolicy.ADMIN_OR_HR;
    public static final long DEFAULT_LINK_EXPIRY_HOURS = 72L;
    public static final int DEFAULT_FILE_RETENTION_DAYS = 365;
    public static final int DEFAULT_AI_LOG_RETENTION_DAYS = 90;
    public static final AuditVisibility DEFAULT_AUDIT_VISIBILITY = AuditVisibility.ADMIN_ONLY;

    public static final long MIN_LINK_EXPIRY_HOURS = 1L;
    public static final long MAX_LINK_EXPIRY_HOURS = 168L;
    public static final int MIN_FILE_RETENTION_DAYS = 30;
    public static final int MAX_FILE_RETENTION_DAYS = 3650;
    public static final int MIN_AI_LOG_RETENTION_DAYS = 7;
    public static final int MAX_AI_LOG_RETENTION_DAYS = 365;

    private final UUID companyId;
    private final ApprovalPolicy approvalPolicy;
    private final long linkExpiryHours;
    private final Map<TaskType, Set<EvidenceType>> evidenceRules;
    private final int fileRetentionDays;
    private final int aiLogRetentionDays;
    private final AuditVisibility auditVisibility;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public CompanySettings(
            UUID companyId,
            ApprovalPolicy approvalPolicy,
            long linkExpiryHours,
            Map<TaskType, Set<EvidenceType>> evidenceRules,
            int fileRetentionDays,
            int aiLogRetentionDays,
            AuditVisibility auditVisibility,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.approvalPolicy = Objects.requireNonNull(approvalPolicy, "approvalPolicy must not be null");
        this.linkExpiryHours = requireRange(
                linkExpiryHours,
                MIN_LINK_EXPIRY_HOURS,
                MAX_LINK_EXPIRY_HOURS,
                "linkExpiryHours"
        );
        this.evidenceRules = immutableEvidenceRules(evidenceRules);
        this.fileRetentionDays = (int) requireRange(
                fileRetentionDays,
                MIN_FILE_RETENTION_DAYS,
                MAX_FILE_RETENTION_DAYS,
                "fileRetentionDays"
        );
        this.aiLogRetentionDays = (int) requireRange(
                aiLogRetentionDays,
                MIN_AI_LOG_RETENTION_DAYS,
                MAX_AI_LOG_RETENTION_DAYS,
                "aiLogRetentionDays"
        );
        this.auditVisibility = Objects.requireNonNull(auditVisibility, "auditVisibility must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public static CompanySettings defaults(UUID companyId, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return new CompanySettings(
                companyId,
                DEFAULT_APPROVAL_POLICY,
                DEFAULT_LINK_EXPIRY_HOURS,
                Map.of(),
                DEFAULT_FILE_RETENTION_DAYS,
                DEFAULT_AI_LOG_RETENTION_DAYS,
                DEFAULT_AUDIT_VISIBILITY,
                now,
                now,
                0L
        );
    }

    public UUID companyId() {
        return companyId;
    }

    public ApprovalPolicy approvalPolicy() {
        return approvalPolicy;
    }

    public long linkExpiryHours() {
        return linkExpiryHours;
    }

    public Map<TaskType, Set<EvidenceType>> evidenceRules() {
        return evidenceRules;
    }

    public int fileRetentionDays() {
        return fileRetentionDays;
    }

    public int aiLogRetentionDays() {
        return aiLogRetentionDays;
    }

    public AuditVisibility auditVisibility() {
        return auditVisibility;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    private static long requireRange(long value, long min, long max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + min + " and " + max
            );
        }
        return value;
    }

    private static Map<TaskType, Set<EvidenceType>> immutableEvidenceRules(
            Map<TaskType, Set<EvidenceType>> evidenceRules
    ) {
        Objects.requireNonNull(evidenceRules, "evidenceRules must not be null");
        EnumMap<TaskType, Set<EvidenceType>> copy = new EnumMap<>(TaskType.class);
        evidenceRules.forEach((taskType, evidenceTypes) -> {
            Objects.requireNonNull(taskType, "evidenceRules taskType must not be null");
            Objects.requireNonNull(evidenceTypes, "evidenceRules evidenceTypes must not be null");
            EnumSet<EvidenceType> evidenceTypeCopy = evidenceTypes.isEmpty()
                    ? EnumSet.noneOf(EvidenceType.class)
                    : EnumSet.copyOf(evidenceTypes);
            if (evidenceTypeCopy.size() != evidenceTypes.size()) {
                throw new IllegalArgumentException("evidenceRules must not contain null evidence types");
            }
            copy.put(taskType, Collections.unmodifiableSet(evidenceTypeCopy));
        });
        return Collections.unmodifiableMap(copy);
    }
}
