package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.port.PasswordResetTokenRepository;
import com.fowoco.server.auth.domain.PasswordResetToken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final EntityManager entityManager;

    public JpaPasswordResetTokenRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(PasswordResetToken token) {
        Objects.requireNonNull(token, "token must not be null");
        entityManager.persist(PasswordResetTokenJpaEntity.fromDomain(token));
        entityManager.flush();
    }

    @Override
    public boolean existsActiveCreatedAfter(UUID userId, Instant cutoff, Instant now) {
        Long count = entityManager.createQuery(
                        """
                        select count(token)
                        from PasswordResetTokenJpaEntity token
                        where token.userId = :userId
                          and token.createdAt >= :cutoff
                          and token.usedAt is null
                          and token.expiresAt > :now
                        """,
                        Long.class
                )
                .setParameter("userId", userId)
                .setParameter("cutoff", cutoff)
                .setParameter("now", now)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHashWithLock(String tokenHash) {
        return entityManager.createQuery(
                        """
                        select token
                        from PasswordResetTokenJpaEntity token
                        where token.tokenHash = :tokenHash
                        """,
                        PasswordResetTokenJpaEntity.class
                )
                .setParameter("tokenHash", tokenHash)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .map(PasswordResetTokenJpaEntity::toDomain);
    }

    @Override
    public void update(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = entityManager.find(
                PasswordResetTokenJpaEntity.class,
                token.passwordResetTokenId(),
                LockModeType.PESSIMISTIC_WRITE
        );
        if (entity == null) {
            throw new IllegalStateException("password reset token was not found");
        }
        entity.applyState(token);
        entityManager.flush();
    }
}
