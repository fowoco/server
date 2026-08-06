package com.fowoco.server.auth.application;

import java.util.Objects;

public record AgreementAcceptance(boolean agreed, String version) {

    public AgreementAcceptance {
        Objects.requireNonNull(version, "version must not be null");
        version = version.strip();
        if (version.isBlank() || version.length() > 40) {
            throw new IllegalArgumentException("version must be 1 to 40 characters");
        }
    }
}
