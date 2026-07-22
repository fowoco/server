package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.UserAccount;
import java.time.Instant;
import java.util.Objects;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UserAccount userAccount, Instant issuedAt);

    final class IssuedAccessToken {

        private final String value;
        private final Instant expiresAt;
        private final long expiresInSeconds;

        public IssuedAccessToken(String value, Instant expiresAt, long expiresInSeconds) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value must not be blank");
            }
            this.value = value;
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            if (expiresInSeconds <= 0) {
                throw new IllegalArgumentException("expiresInSeconds must be positive");
            }
            this.expiresInSeconds = expiresInSeconds;
        }

        public String value() {
            return value;
        }

        public Instant expiresAt() {
            return expiresAt;
        }

        public long expiresInSeconds() {
            return expiresInSeconds;
        }
    }
}
