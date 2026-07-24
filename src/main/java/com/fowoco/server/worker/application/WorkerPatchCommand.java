package com.fowoco.server.worker.application;

import com.fowoco.server.worker.domain.WorkerStatus;
import java.time.LocalDate;
import java.util.UUID;

public final class WorkerPatchCommand {

    private final UUID workerId;
    private final UUID companyId;
    private final String displayName;
    private final String nationalityCode;
    private final String preferredLanguage;
    private final WorkerStatus workStatus;
    private final LocalDate stayExpiryDate;
    private final LocalDate contractStartDate;
    private final LocalDate contractEndDate;
    private final long expectedVersion;

    public WorkerPatchCommand(
            UUID workerId,
            UUID companyId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            LocalDate stayExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            long expectedVersion
    ) {
        this.workerId = workerId;
        this.companyId = companyId;
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.workStatus = workStatus;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.expectedVersion = expectedVersion;
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

    public LocalDate stayExpiryDate() {
        return stayExpiryDate;
    }

    public LocalDate contractStartDate() {
        return contractStartDate;
    }

    public LocalDate contractEndDate() {
        return contractEndDate;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
