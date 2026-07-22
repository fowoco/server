package com.fowoco.server.auth.domain;

public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED;

    public boolean allowsAuthentication() {
        return this == ACTIVE;
    }
}
