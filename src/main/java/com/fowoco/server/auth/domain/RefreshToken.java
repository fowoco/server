package com.fowoco.server.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RefreshToken {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UUID refreshTokenId;
    private final UUID userId;
    private final UUID companyId;
    private final UUID tokenFamilyId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final Instant usedAt;
    private final Instant revokedAt;
    private final UUID replacedByTokenId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public RefreshToken(
            UUID refreshTokenId,
            UUID userId,
            UUID companyId,
            UUID tokenFamilyId,
            String tokenHash,
            Instant expiresAt,
            Instant usedAt,
            Instant revokedAt,
            UUID replacedByTokenId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.refreshTokenId = Objects.requireNonNull(refreshTokenId, "refreshTokenId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.tokenFamilyId = Objects.requireNonNull(tokenFamilyId, "tokenFamilyId must not be null");
        this.tokenHash = requireTokenHash(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
        this.replacedByTokenId = replacedByTokenId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        validateTimeline();
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public static RefreshToken issue(
            UUID refreshTokenId,
            UUID userId,
            UUID companyId,
            UUID tokenFamilyId,
            String tokenHash,
            Instant expiresAt,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        return new RefreshToken(
                refreshTokenId,
                userId,
                companyId,
                tokenFamilyId,
                tokenHash,
                expiresAt,
                null,
                null,
                null,
                now,
                now,
                0L
        );
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActiveAt(Instant now) {
        return !isUsed() && !isRevoked() && !isExpiredAt(now);
    }

    public RefreshToken rotateTo(RefreshToken replacementToken, Instant now) {
        Objects.requireNonNull(replacementToken, "replacementToken must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (!isActiveAt(now)) {
            throw new IllegalStateException("only an active refresh token can be rotated");
        }
        if (!replacementToken.isActiveAt(now)) {
            throw new IllegalArgumentException("replacementToken must be active");
        }
        if (refreshTokenId.equals(replacementToken.refreshTokenId)) {
            throw new IllegalArgumentException("replacement token must differ from the current token");
        }
        if (!userId.equals(replacementToken.userId) || !companyId.equals(replacementToken.companyId)) {
            throw new IllegalArgumentException("replacement token must belong to the same account");
        }
        if (!tokenFamilyId.equals(replacementToken.tokenFamilyId)) {
            throw new IllegalArgumentException("replacement token must belong to the same token family");
        }
        return new RefreshToken(
                refreshTokenId,
                userId,
                companyId,
                tokenFamilyId,
                tokenHash,
                expiresAt,
                now,
                revokedAt,
                replacementToken.refreshTokenId,
                createdAt,
                now,
                version
        );
    }

    public RefreshToken revoke(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (now.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        if (isRevoked()) {
            return this;
        }
        return new RefreshToken(
                refreshTokenId,
                userId,
                companyId,
                tokenFamilyId,
                tokenHash,
                expiresAt,
                usedAt,
                now,
                replacedByTokenId,
                createdAt,
                now,
                version
        );
    }

    public UUID refreshTokenId() {
        return refreshTokenId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID tokenFamilyId() {
        return tokenFamilyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public UUID replacedByTokenId() {
        return replacedByTokenId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    private static String requireTokenHash(String tokenHash) {
        if (tokenHash == null || !SHA_256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 hex value");
        }
        return tokenHash;
    }

    private void validateTimeline() {
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (usedAt != null && usedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("usedAt must not be before createdAt");
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        if (usedAt != null && updatedAt.isBefore(usedAt)) {
            throw new IllegalArgumentException("updatedAt must not be before usedAt");
        }
        if (revokedAt != null && updatedAt.isBefore(revokedAt)) {
            throw new IllegalArgumentException("updatedAt must not be before revokedAt");
        }
        if (replacedByTokenId != null && usedAt == null) {
            throw new IllegalArgumentException("usedAt is required when a replacement token exists");
        }
        if (refreshTokenId.equals(replacedByTokenId)) {
            throw new IllegalArgumentException("a refresh token cannot replace itself");
        }
    }
}
