package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.domain.RefreshToken;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final EntityManager entityManager;

    public JpaRefreshTokenRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(RefreshToken refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        entityManager.persist(RefreshTokenJpaEntity.fromDomain(refreshToken));
    }
}
