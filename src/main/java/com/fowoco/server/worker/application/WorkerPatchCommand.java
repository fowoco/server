package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.WorkerStatus;
import java.time.LocalDate;
import java.util.UUID;

public final class WorkerPatchCommand {

    private final UUID workerId;
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
    private final long expectedVersion;

    public WorkerPatchCommand(
            UUID workerId,
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
            long expectedVersion
    ) {
        this.workerId = workerId;
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.workStatus = workStatus;
        this.visaType = visaType;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.employmentPermitEndDate = employmentPermitEndDate;
        this.employmentActivityEndDate = employmentActivityEndDate;
        this.expectedVersion = expectedVersion;
    }

    public UUID workerId() {
        return workerId;
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

    public long expectedVersion() {
        return expectedVersion;
    }
}
