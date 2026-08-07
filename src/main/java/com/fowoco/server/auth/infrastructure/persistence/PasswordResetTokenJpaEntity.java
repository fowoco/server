package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.domain.PasswordResetToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetTokenJpaEntity {

    @Id
    @Column(name = "password_reset_token_id", nullable = false, updatable = false)
    private UUID passwordResetTokenId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PasswordResetTokenJpaEntity() {
    }

    private PasswordResetTokenJpaEntity(PasswordResetToken token) {
        passwordResetTokenId = token.passwordResetTokenId();
        companyId = token.companyId();
        userId = token.userId();
        tokenHash = token.tokenHash();
        expiresAt = token.expiresAt();
        usedAt = token.usedAt();
        createdAt = token.createdAt();
        updatedAt = token.updatedAt();
        version = token.version();
    }

    static PasswordResetTokenJpaEntity fromDomain(PasswordResetToken token) {
        return new PasswordResetTokenJpaEntity(token);
    }

    PasswordResetToken toDomain() {
        return new PasswordResetToken(
                passwordResetTokenId,
                companyId,
                userId,
                tokenHash,
                expiresAt,
                usedAt,
                createdAt,
                updatedAt,
                version
        );
    }

    void applyState(PasswordResetToken token) {
        if (!passwordResetTokenId.equals(token.passwordResetTokenId()) || version + 1 != token.version()) {
            throw new IllegalArgumentException("password reset token version transition is invalid");
        }
        usedAt = token.usedAt();
        updatedAt = token.updatedAt();
    }
}
