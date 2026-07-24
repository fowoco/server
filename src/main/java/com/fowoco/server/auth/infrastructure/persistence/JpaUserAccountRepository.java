package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.domain.UserAccount;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserAccountRepository
        implements com.fowoco.server.auth.application.port.UserAccountRepository {

    private final EntityManager entityManager;

    public JpaUserAccountRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(UserAccount userAccount) {
        Objects.requireNonNull(userAccount, "userAccount must not be null");
        entityManager.persist(UserAccountJpaEntity.fromDomain(userAccount));
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
                .map(UserAccountJpaEntity::toDomain);
    }

    @Override
    public Optional<UserAccount> findByUserIdAndCompanyId(UUID userId, UUID companyId) {
        return entityManager.createQuery(
                        """
                        select userAccount
                        from UserAccountJpaEntity userAccount
                        where userAccount.userId = :userId
                          and userAccount.companyId = :companyId
                        """,
                        UserAccountJpaEntity.class
                )
                .setParameter("userId", userId)
                .setParameter("companyId", companyId)
                .getResultStream()
                .findFirst()
                .map(UserAccountJpaEntity::toDomain);
    }
}
