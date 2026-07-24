package com.fowoco.server.worker.application;

import java.time.LocalDate;
import java.util.UUID;

public final class WorkerCreateCommand {

    private final UUID companyId;
    private final String displayName;
    private final String nationalityCode;
    private final String preferredLanguage;
    private final LocalDate stayExpiryDate;
    private final LocalDate contractStartDate;
    private final LocalDate contractEndDate;

    public WorkerCreateCommand(
            UUID companyId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            LocalDate stayExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate
    ) {
        this.companyId = companyId;
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
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

    public LocalDate stayExpiryDate() {
        return stayExpiryDate;
    }

    public LocalDate contractStartDate() {
        return contractStartDate;
    }

    public LocalDate contractEndDate() {
        return contractEndDate;
    }
}
