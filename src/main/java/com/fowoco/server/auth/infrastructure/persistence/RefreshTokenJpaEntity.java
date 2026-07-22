package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.domain.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_refresh_token_hash",
                columnNames = "token_hash"
        )
)
public class RefreshTokenJpaEntity {

    @Id
    @Column(name = "refresh_token_id", nullable = false, updatable = false)
    private UUID refreshTokenId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "token_family_id", nullable = false, updatable = false)
    private UUID tokenFamilyId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RefreshTokenJpaEntity() {
    }

    private RefreshTokenJpaEntity(
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
        this.refreshTokenId = refreshTokenId;
        this.userId = userId;
        this.companyId = companyId;
        this.tokenFamilyId = tokenFamilyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
        this.replacedByTokenId = replacedByTokenId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static RefreshTokenJpaEntity fromDomain(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        return new RefreshTokenJpaEntity(
                refreshToken.refreshTokenId(),
                refreshToken.userId(),
                refreshToken.companyId(),
                refreshToken.tokenFamilyId(),
                refreshToken.tokenHash(),
                refreshToken.expiresAt(),
                refreshToken.usedAt(),
                refreshToken.revokedAt(),
                refreshToken.replacedByTokenId(),
                refreshToken.createdAt(),
                refreshToken.updatedAt(),
                refreshToken.version()
        );
    }

    public RefreshToken toDomain() {
        return new RefreshToken(
                refreshTokenId,
                userId,
                companyId,
                tokenFamilyId,
                tokenHash,
                expiresAt,
                usedAt,
                revokedAt,
                replacedByTokenId,
                createdAt,
                updatedAt,
                version
        );
    }

}
