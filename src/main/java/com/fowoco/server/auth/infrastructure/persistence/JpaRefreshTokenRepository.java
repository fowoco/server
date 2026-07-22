package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.domain.RefreshToken;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final EntityManager entityManager;

    public JpaRefreshTokenRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insert(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        entityManager.persist(RefreshTokenJpaEntity.fromDomain(refreshToken));
        entityManager.flush();
    }

    @Override
    public Optional<RefreshToken> findByTokenHashWithFamilyLock(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Optional<UUID> tokenFamilyId = entityManager.createQuery(
                        """
                        select refreshToken.tokenFamilyId
                        from RefreshTokenJpaEntity refreshToken
                        where refreshToken.tokenHash = :tokenHash
                        """,
                        UUID.class
                )
                .setParameter("tokenHash", tokenHash)
                .getResultStream()
                .findFirst();
        if (tokenFamilyId.isEmpty()) {
            return Optional.empty();
        }

        List<RefreshTokenJpaEntity> lockedFamily = entityManager.createQuery(
                        """
                        select refreshToken
                        from RefreshTokenJpaEntity refreshToken
                        where refreshToken.tokenFamilyId = :tokenFamilyId
                        order by refreshToken.createdAt, refreshToken.refreshTokenId
                        """,
                        RefreshTokenJpaEntity.class
                )
                .setParameter("tokenFamilyId", tokenFamilyId.orElseThrow())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        return lockedFamily.stream()
                .filter(refreshToken -> tokenHash.equals(refreshToken.toDomain().tokenHash()))
                .findFirst()
                .map(RefreshTokenJpaEntity::toDomain);
    }

    @Override
    public void update(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        RefreshTokenJpaEntity entity = entityManager.find(
                RefreshTokenJpaEntity.class,
                refreshToken.refreshTokenId(),
                LockModeType.PESSIMISTIC_WRITE
        );
        if (entity == null) {
            throw new IllegalStateException("refresh token to update was not found");
        }
        entity.applyState(refreshToken);
    }

    @Override
    public int revokeFamily(UUID tokenFamilyId, Instant revokedAt) {
        Objects.requireNonNull(tokenFamilyId, "tokenFamilyId must not be null");
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        int updatedRows = entityManager.createQuery(
                        """
                        update RefreshTokenJpaEntity refreshToken
                        set refreshToken.revokedAt = :revokedAt,
                            refreshToken.updatedAt = :revokedAt,
                            refreshToken.version = refreshToken.version + 1
                        where refreshToken.tokenFamilyId = :tokenFamilyId
                          and refreshToken.revokedAt is null
                        """
                )
                .setParameter("revokedAt", revokedAt)
                .setParameter("tokenFamilyId", tokenFamilyId)
                .executeUpdate();
        entityManager.clear();
        return updatedRows;
    }
}
