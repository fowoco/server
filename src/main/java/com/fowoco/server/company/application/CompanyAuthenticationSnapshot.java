package com.fowoco.server.company.application;

import java.util.Objects;
import java.util.UUID;

public final class CompanyAuthenticationSnapshot {

    private final UUID companyId;
    private final String companyName;
    private final boolean authenticationAllowed;

    public CompanyAuthenticationSnapshot(
            UUID companyId,
            String companyName,
            boolean authenticationAllowed
    ) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName must not be blank");
        }
        this.companyName = companyName;
        this.authenticationAllowed = authenticationAllowed;
    }

    public UUID companyId() {
        return companyId;
    }

    public String companyName() {
        return companyName;
    }

    public boolean authenticationAllowed() {
        return authenticationAllowed;
    }
}
