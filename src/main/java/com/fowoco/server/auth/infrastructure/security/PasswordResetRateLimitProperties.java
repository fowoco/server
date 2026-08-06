package com.fowoco.server.auth.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.password-reset.rate-limit")
public final class PasswordResetRateLimitProperties {

    private final int maxRequests;
    private final Duration window;

    public PasswordResetRateLimitProperties(int maxRequests, Duration window) {
        if (maxRequests < 1 || maxRequests > 1000) {
            throw new IllegalArgumentException("password reset maxRequests must be between 1 and 1000");
        }
        if (window == null || window.isNegative() || window.isZero() || window.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("password reset rate-limit window must be between 1 millisecond and 1 day");
        }
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public int maxRequests() {
        return maxRequests;
    }

    public Duration window() {
        return window;
    }
}
