package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.domain.AccountStatus;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "user_account",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_account_normalized_email",
                        columnNames = "normalized_email"
                ),
                @UniqueConstraint(
                        name = "uq_user_account_user_company",
                        columnNames = {"user_id", "company_id"}
                )
        }
)
public class UserAccountJpaEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserAccountJpaEntity() {
    }

    private UserAccountJpaEntity(
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
        this.userId = userId;
        this.companyId = companyId;
        this.displayName = displayName;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static UserAccountJpaEntity fromDomain(UserAccount userAccount) {
        Objects.requireNonNull(userAccount, "userAccount must not be null");
        return new UserAccountJpaEntity(
                userAccount.userId(),
                userAccount.companyId(),
                userAccount.displayName(),
                userAccount.email(),
                userAccount.normalizedEmail(),
                userAccount.passwordHash(),
                userAccount.role(),
                userAccount.status(),
                userAccount.createdAt(),
                userAccount.updatedAt(),
                userAccount.version()
        );
    }

    public UserAccount toDomain() {
        return new UserAccount(
                userId,
                companyId,
                displayName,
                email,
                normalizedEmail,
                passwordHash,
                role,
                status,
                createdAt,
                updatedAt,
                version
        );
    }

}
