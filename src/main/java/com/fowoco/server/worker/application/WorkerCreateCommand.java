package com.fowoco.server.worker.application;

import java.time.LocalDate;

public final class WorkerCreateCommand {

    private final String displayName;
    private final String nationalityCode;
    private final String preferredLanguage;
    private final String visaType;
    private final LocalDate stayExpiryDate;
    private final LocalDate contractStartDate;
    private final LocalDate contractEndDate;
    private final LocalDate employmentPermitEndDate;
    private final LocalDate employmentActivityEndDate;

    public WorkerCreateCommand(
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            String visaType,
            LocalDate stayExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            LocalDate employmentPermitEndDate,
            LocalDate employmentActivityEndDate
    ) {
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.visaType = visaType;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.employmentPermitEndDate = employmentPermitEndDate;
        this.employmentActivityEndDate = employmentActivityEndDate;
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
}
