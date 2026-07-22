package com.fowoco.server.auth.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh-token")
public final class RefreshTokenProperties {

    private static final Duration MINIMUM_TTL = Duration.ofDays(1);
    private static final Duration MAXIMUM_TTL = Duration.ofDays(90);

    private final Duration ttl;

    public RefreshTokenProperties(Duration ttl) {
        if (ttl == null || ttl.compareTo(MINIMUM_TTL) < 0 || ttl.compareTo(MAXIMUM_TTL) > 0) {
            throw new IllegalArgumentException("refresh token ttl must be between 1 and 90 days");
        }
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }
}
