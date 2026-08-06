package com.fowoco.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.auth.infrastructure.security.PasswordResetRateLimitProperties;
import com.fowoco.server.auth.infrastructure.security.Sha256PasswordResetTokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PasswordResetRequestRateLimiterTest {

    @Test
    void rejectsRequestsBeyondTheIpWindowLimit() {
        PasswordResetRequestRateLimiter limiter = new PasswordResetRequestRateLimiter(
                new PasswordResetRateLimitProperties(2, Duration.ofMinutes(10)),
                new Sha256PasswordResetTokenHasher(),
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.10")).isTrue();
        assertThat(limiter.tryAcquire("203.0.113.10")).isFalse();
        assertThat(limiter.tryAcquire("203.0.113.11")).isTrue();
    }
}
