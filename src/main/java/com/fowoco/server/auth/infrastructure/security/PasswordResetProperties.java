package com.fowoco.server.auth.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.password-reset")
public final class PasswordResetProperties {

    private final Duration ttl;
    private final Duration cooldown;
    private final Duration minimumResponseTime;

    public PasswordResetProperties(Duration ttl, Duration cooldown, Duration minimumResponseTime) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("password reset ttl must be between 1 millisecond and 24 hours");
        }
        if (cooldown == null || cooldown.isNegative() || cooldown.compareTo(ttl) >= 0) {
            throw new IllegalArgumentException("password reset cooldown must be non-negative and shorter than ttl");
        }
        if (minimumResponseTime == null || minimumResponseTime.isNegative()
                || minimumResponseTime.compareTo(Duration.ofSeconds(5)) > 0) {
            throw new IllegalArgumentException("password reset minimumResponseTime must be between 0 and 5 seconds");
        }
        this.ttl = ttl;
        this.cooldown = cooldown;
        this.minimumResponseTime = minimumResponseTime;
    }

    public Duration ttl() {
        return ttl;
    }

    public Duration cooldown() {
        return cooldown;
    }

    public Duration minimumResponseTime() {
        return minimumResponseTime;
    }
}
