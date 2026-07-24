package com.fowoco.server.auth.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class UserAccount {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_DISPLAY_NAME_LENGTH = 80;
    private static final int MAX_PASSWORD_HASH_LENGTH = 255;

    private final UUID userId;
    private final UUID companyId;
    private final String displayName;
    private final String email;
    private final String normalizedEmail;
    private final String passwordHash;
    private final UserRole role;
    private final AccountStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public UserAccount(
            UUID userId,
            UUID companyId,
            String displayName,
            String email,
            String normalizedEmail,
            String passwordHash,
            UserRole role,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.displayName = requireDisplayName(displayName);
        this.email = requireEmail(email);
        String expectedNormalizedEmail = normalizeEmail(this.email);
        if (!expectedNormalizedEmail.equals(normalizedEmail)) {
            throw new IllegalArgumentException("normalizedEmail does not match email");
        }
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = requirePasswordHash(passwordHash);
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public static UserAccount create(
            UUID userId,
            UUID companyId,
            String displayName,
            String email,
            String passwordHash,
            UserRole role,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        return new UserAccount(
                userId,
                companyId,
                displayName,
                email,
                normalizeEmail(email),
                passwordHash,
                role,
                AccountStatus.ACTIVE,
                now,
                now,
                0L
        );
    }

    public static String normalizeEmail(String email) {
        return requireEmail(email).toLowerCase(Locale.ROOT);
    }

    public boolean canLogin() {
        return status.allowsAuthentication();
    }

    public boolean belongsTo(UUID companyId) {
        return this.companyId.equals(companyId);
    }

    public UUID userId() {
        return userId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String displayName() {
        return displayName;
    }

    public String email() {
        return email;
    }

    public String normalizedEmail() {
        return normalizedEmail;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserRole role() {
        return role;
    }

    public AccountStatus status() {
        return status;
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

    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        String stripped = email.strip();
        if (stripped.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("email must not exceed " + MAX_EMAIL_LENGTH + " characters");
        }
        return stripped;
    }

    private static String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        String normalized = displayName.strip();
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "displayName must not exceed " + MAX_DISPLAY_NAME_LENGTH + " characters"
            );
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("displayName must not contain control characters");
        }
        return normalized;
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        if (passwordHash.length() > MAX_PASSWORD_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "passwordHash must not exceed " + MAX_PASSWORD_HASH_LENGTH + " characters"
            );
        }
        return passwordHash;
    }
}
