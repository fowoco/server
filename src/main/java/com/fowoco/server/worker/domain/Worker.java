package com.fowoco.server.worker.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class Worker {

    private static final int MAX_DISPLAY_NAME_LENGTH = 120;

    private final UUID workerId;
    private final UUID companyId;
    private final String displayName;
    private final String nationalityCode;
    private final String preferredLanguage;
    private final WorkerStatus workStatus;
    private final String visaType;
    private final LocalDate stayExpiryDate;
    private final LocalDate contractStartDate;
    private final LocalDate contractEndDate;
    private final LocalDate employmentPermitEndDate;
    private final LocalDate employmentActivityEndDate;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public Worker(
            UUID workerId,
            UUID companyId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            String visaType,
            LocalDate stayExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            LocalDate employmentPermitEndDate,
            LocalDate employmentActivityEndDate,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.displayName = requireDisplayName(displayName);
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.workStatus = Objects.requireNonNull(workStatus, "workStatus must not be null");
        this.visaType = visaType;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.employmentPermitEndDate = employmentPermitEndDate;
        this.employmentActivityEndDate = employmentActivityEndDate;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (contractStartDate != null && contractEndDate != null
                && contractEndDate.isBefore(contractStartDate)) {
            throw new IllegalArgumentException("contractEndDate must not be before contractStartDate");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public static Worker create(
            UUID workerId,
            UUID companyId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            String visaType,
            LocalDate stayExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            LocalDate employmentPermitEndDate,
            LocalDate employmentActivityEndDate,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        return new Worker(
                workerId,
                companyId,
                displayName,
                nationalityCode,
                preferredLanguage,
                WorkerStatus.ACTIVE,
                visaType,
                stayExpiryDate,
                contractStartDate,
                contractEndDate,
                employmentPermitEndDate,
                employmentActivityEndDate,
                now,
                now,
                0L
        );
    }

    public boolean isCurrentlyEmployed() {
        return workStatus.isCurrentlyEmployed();
    }

    public UUID workerId() {
        return workerId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String displayName() {
        return displayName;
    }

    public String nationalityCode() {
        return nationalityCode;
    }

    public String preferredLanguage() {
        return preferredLanguage;
    }

    public WorkerStatus workStatus() {
        return workStatus;
    }

    public String visaType() {
        return visaType;
    }

    public LocalDate stayExpiryDate() {
        return stayExpiryDate;
    }

    public LocalDate contractStartDate() {
        return contractStartDate;
    }

    public LocalDate contractEndDate() {
        return contractEndDate;
    }

    public LocalDate employmentPermitEndDate() {
        return employmentPermitEndDate;
    }

    public LocalDate employmentActivityEndDate() {
        return employmentActivityEndDate;
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

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        String normalized = displayName.strip();
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "displayName must not exceed " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        return normalized;
    }
}
