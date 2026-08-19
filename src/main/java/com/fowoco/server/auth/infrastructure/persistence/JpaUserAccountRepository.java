package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.infrastructure.crypto.AccountPiiCipher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserAccountRepository
        implements com.fowoco.server.auth.application.port.UserAccountRepository {

    private final EntityManager entityManager;
    private final AccountPiiCipher piiCipher;

    public JpaUserAccountRepository(EntityManager entityManager, AccountPiiCipher piiCipher) {
        this.entityManager = entityManager;
        this.piiCipher = Objects.requireNonNull(piiCipher, "piiCipher must not be null");
    }

    @Override
    public void insert(UserAccount userAccount) {
        Objects.requireNonNull(userAccount, "userAccount must not be null");
        entityManager.persist(UserAccountJpaEntity.fromDomain(userAccount, piiCipher));
        entityManager.flush();
    }

    @Override
    public void update(UserAccount userAccount) {
        Objects.requireNonNull(userAccount, "userAccount must not be null");
        UserAccountJpaEntity entity = entityManager.find(
                UserAccountJpaEntity.class,
                userAccount.userId(),
                LockModeType.PESSIMISTIC_WRITE
        );
        if (entity == null) {
            throw new IllegalStateException("user account to update was not found");
        }
        entity.applyState(userAccount, piiCipher);
        entityManager.flush();
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
        Long count = entityManager.createQuery(
                        """
                        select count(userAccount)
                        from UserAccountJpaEntity userAccount
                        where userAccount.normalizedEmail = :normalizedEmail
                        """,
                        Long.class
                )
                .setParameter("normalizedEmail", normalizedEmail)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
        return entityManager.createQuery(
                        """
                        select userAccount
                        from UserAccountJpaEntity userAccount
                        where userAccount.normalizedEmail = :normalizedEmail
                        """,
                        UserAccountJpaEntity.class
                )
                .setParameter("normalizedEmail", normalizedEmail)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(entity -> entity.toDomain(piiCipher));
    }

    @Override
    public Optional<UserAccount> findByNormalizedEmailWithLock(String normalizedEmail) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail must not be null");
        return entityManager.createQuery(
                        """
                        select userAccount
                        from UserAccountJpaEntity userAccount
                        where userAccount.normalizedEmail = :normalizedEmail
                        """,
                        UserAccountJpaEntity.class
                )
                .setParameter("normalizedEmail", normalizedEmail)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(entity -> entity.toDomain(piiCipher));
    }

    @Override
    public Optional<UserAccount> findByUserIdAndCompanyId(UUID userId, UUID companyId) {
        return findByUserIdAndCompanyId(userId, companyId, null);
    }

    @Override
    public Optional<UserAccount> findByUserIdAndCompanyIdWithLock(UUID userId, UUID companyId) {
        return findByUserIdAndCompanyId(userId, companyId, LockModeType.PESSIMISTIC_WRITE);
    }

    private Optional<UserAccount> findByUserIdAndCompanyId(
            UUID userId,
            UUID companyId,
            LockModeType lockMode
    ) {
        var query = entityManager.createQuery(
                        """
                        select userAccount
                        from UserAccountJpaEntity userAccount
                        where userAccount.userId = :userId
                          and userAccount.companyId = :companyId
                        """,
                        UserAccountJpaEntity.class
                )
                .setParameter("userId", userId)
                .setParameter("companyId", companyId);
        if (lockMode != null) {
            query.setLockMode(lockMode);
        }
        return query
                .getResultStream()
                .findFirst()
                .map(entity -> entity.toDomain(piiCipher));
    }
}
