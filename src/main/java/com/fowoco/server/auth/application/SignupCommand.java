package com.fowoco.server.auth.application;

import java.util.Objects;

public record SignupCommand(
        String companyName,
        String displayName,
        String email,
        String password
) {

    public SignupCommand {
        Objects.requireNonNull(companyName, "companyName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }
}
