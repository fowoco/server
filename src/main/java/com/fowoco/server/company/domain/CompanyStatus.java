package com.fowoco.server.company.domain;

public enum CompanyStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED;

    public boolean allowsAuthentication() {
        return this == ACTIVE;
    }
}
