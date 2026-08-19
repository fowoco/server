package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.domain.AccountStatus;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.auth.infrastructure.crypto.AccountPiiCipher;
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

    private static final String PHONE_FIELD = "phone";

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "phone_ciphertext")
    private String phoneCiphertext;

    @Column(name = "phone_key_version", length = 60)
    private String phoneKeyVersion;

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

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserAccountJpaEntity() {
    }

    private UserAccountJpaEntity(
            UUID userId,
            UUID companyId,
            String displayName,
            String phone,
            String phoneCiphertext,
            String phoneKeyVersion,
            String email,
            String normalizedEmail,
            String passwordHash,
            UserRole role,
            AccountStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant passwordChangedAt,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastFailedLoginAt,
            long version
    ) {
        this.userId = userId;
        this.companyId = companyId;
        this.displayName = displayName;
        this.phone = phone;
        this.phoneCiphertext = phoneCiphertext;
        this.phoneKeyVersion = phoneKeyVersion;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.passwordChangedAt = passwordChangedAt;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastFailedLoginAt = lastFailedLoginAt;
        this.version = version;
    }

    public static UserAccountJpaEntity fromDomain(
            UserAccount userAccount,
            AccountPiiCipher piiCipher
    ) {
        Objects.requireNonNull(userAccount, "userAccount must not be null");
        UserAccountJpaEntity entity = new UserAccountJpaEntity(
                userAccount.userId(),
                userAccount.companyId(),
                userAccount.displayName(),
                null,
                null,
                null,
                userAccount.email(),
                userAccount.normalizedEmail(),
                userAccount.passwordHash(),
                userAccount.role(),
                userAccount.status(),
                userAccount.createdAt(),
                userAccount.updatedAt(),
                userAccount.passwordChangedAt(),
                userAccount.failedLoginAttempts(),
                userAccount.lockedUntil(),
                userAccount.lastFailedLoginAt(),
                userAccount.version()
        );
        entity.storePhone(userAccount.phone(), piiCipher);
        return entity;
    }

    public UserAccount toDomain(AccountPiiCipher piiCipher) {
        return new UserAccount(
                userId,
                companyId,
                displayName,
                readPhone(piiCipher),
                email,
                normalizedEmail,
                passwordHash,
                role,
                status,
                createdAt,
                updatedAt,
                passwordChangedAt,
                failedLoginAttempts,
                lockedUntil,
                lastFailedLoginAt,
                version
        );
    }

    void applyState(UserAccount userAccount, AccountPiiCipher piiCipher) {
        Objects.requireNonNull(userAccount, "userAccount must not be null");
        if (!userId.equals(userAccount.userId()) || version + 1 != userAccount.version()) {
            throw new IllegalArgumentException("user account version transition is invalid");
        }
        displayName = userAccount.displayName();
        storePhone(userAccount.phone(), piiCipher);
        passwordHash = userAccount.passwordHash();
        updatedAt = userAccount.updatedAt();
        passwordChangedAt = userAccount.passwordChangedAt();
        failedLoginAttempts = userAccount.failedLoginAttempts();
        lockedUntil = userAccount.lockedUntil();
        lastFailedLoginAt = userAccount.lastFailedLoginAt();
    }

    private String readPhone(AccountPiiCipher piiCipher) {
        Objects.requireNonNull(piiCipher, "piiCipher must not be null");
        if (phoneCiphertext != null) {
            return piiCipher.decrypt(
                    phoneCiphertext,
                    phoneKeyVersion,
                    companyId,
                    userId,
                    PHONE_FIELD
            );
        }
        if (phone != null && piiCipher.isAvailable()) {
            String legacyPhone = phone;
            storePhone(legacyPhone, piiCipher);
            return legacyPhone;
        }
        return phone;
    }

    private void storePhone(String value, AccountPiiCipher piiCipher) {
        Objects.requireNonNull(piiCipher, "piiCipher must not be null");
        if (value == null) {
            phone = null;
            phoneCiphertext = null;
            phoneKeyVersion = null;
            return;
        }
        if (!piiCipher.isAvailable()) {
            phone = value;
            phoneCiphertext = null;
            phoneKeyVersion = null;
            return;
        }
        AccountPiiCipher.EncryptedValue encrypted = piiCipher.encrypt(
                value,
                companyId,
                userId,
                PHONE_FIELD
        );
        phone = null;
        phoneCiphertext = encrypted.ciphertext();
        phoneKeyVersion = encrypted.keyVersion();
    }

}
