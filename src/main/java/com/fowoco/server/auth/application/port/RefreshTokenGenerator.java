package com.fowoco.server.auth.application.port;

import java.time.Instant;
import java.util.Objects;

public interface RefreshTokenGenerator {

    GeneratedRefreshToken generate(Instant issuedAt);

    final class GeneratedRefreshToken {

        private final String rawValue;
        private final String tokenHash;
        private final Instant expiresAt;

        public GeneratedRefreshToken(String rawValue, String tokenHash, Instant expiresAt) {
            if (rawValue == null || rawValue.isBlank()) {
                throw new IllegalArgumentException("rawValue must not be blank");
            }
            if (tokenHash == null || tokenHash.isBlank()) {
                throw new IllegalArgumentException("tokenHash must not be blank");
            }
            this.rawValue = rawValue;
            this.tokenHash = tokenHash;
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        }

        public String rawValue() {
            return rawValue;
        }

        public String tokenHash() {
            return tokenHash;
        }

        public Instant expiresAt() {
            return expiresAt;
        }
    }
}
