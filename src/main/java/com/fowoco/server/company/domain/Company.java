package com.fowoco.server.company.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Company {

    private static final int MAX_NAME_LENGTH = 120;

    private final UUID companyId;
    private final String name;
    private final CompanyStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long version;

    public Company(
            UUID companyId,
            String name,
            CompanyStatus status,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.name = requireName(name);
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

    public static Company create(UUID companyId, String name, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return new Company(companyId, name, CompanyStatus.ACTIVE, now, now, 0L);
    }

    public boolean isActive() {
        return status.allowsAuthentication();
    }

    public UUID companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public CompanyStatus status() {
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

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        String normalized = name.strip();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must not exceed " + MAX_NAME_LENGTH + " characters");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("name must not contain control characters");
        }
        return normalized;
    }
}
