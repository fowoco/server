package com.fowoco.server.auth.infrastructure.security;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.login-protection")
public final class LoginProtectionProperties {

    private final int maxFailedAttempts;
    private final Duration lockDuration;
    private final Duration passwordMaxAge;

    public LoginProtectionProperties(
            int maxFailedAttempts,
            Duration lockDuration,
            Duration passwordMaxAge
    ) {
        if (maxFailedAttempts < 1 || maxFailedAttempts > 20) {
            throw new IllegalArgumentException("maxFailedAttempts must be between 1 and 20");
        }
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = requirePositive(lockDuration, "lockDuration");
        this.passwordMaxAge = requirePositive(passwordMaxAge, "passwordMaxAge");
    }

    public int maxFailedAttempts() {
        return maxFailedAttempts;
    }

    public Duration lockDuration() {
        return lockDuration;
    }

    public Duration passwordMaxAge() {
        return passwordMaxAge;
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
