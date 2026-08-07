package com.fowoco.server.auth.application;

import java.time.Instant;
import java.util.Objects;

public record PasswordResetDispatch(String email, String rawToken, Instant expiresAt) {

    public PasswordResetDispatch {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
