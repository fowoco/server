package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void insert(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHashWithFamilyLock(String tokenHash);

    void update(RefreshToken refreshToken);

    int revokeFamily(UUID tokenFamilyId, Instant revokedAt);
}
