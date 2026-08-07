package com.fowoco.server.auth.application;

import java.util.Objects;

public record SignupCommand(
        String companyName,
        String displayName,
        String email,
        String password,
        SignupAgreements agreements,
        String requestId
) {

    public SignupCommand {
        Objects.requireNonNull(companyName, "companyName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(agreements, "agreements must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        requestId = requestId.strip();
        if (requestId.isBlank() || requestId.length() > 128) {
            throw new IllegalArgumentException("requestId must be 1 to 128 characters");
        }
    }
}
