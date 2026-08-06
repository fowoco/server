package com.fowoco.server.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PasswordResetToken(
        UUID passwordResetTokenId,
        UUID companyId,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public PasswordResetToken {
        Objects.requireNonNull(passwordResetTokenId, "passwordResetTokenId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (tokenHash == null || !tokenHash.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 value");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (usedAt != null && usedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("usedAt must not be before createdAt");
        }
        if (updatedAt.isBefore(createdAt) || (usedAt != null && updatedAt.isBefore(usedAt))) {
            throw new IllegalArgumentException("updatedAt is inconsistent");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public static PasswordResetToken issue(
            UUID tokenId,
            UUID companyId,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant now
    ) {
        return new PasswordResetToken(
                tokenId, companyId, userId, tokenHash, expiresAt, null, now, now, 0L
        );
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public PasswordResetToken markUsed(Instant now) {
        if (!isUsableAt(now)) {
            throw new IllegalStateException("password reset token is not usable");
        }
        return new PasswordResetToken(
                passwordResetTokenId,
                companyId,
                userId,
                tokenHash,
                expiresAt,
                now,
                createdAt,
                now,
                version + 1
        );
    }
}
