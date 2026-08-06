package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    void insert(PasswordResetToken token);

    boolean existsActiveCreatedAfter(UUID userId, Instant cutoff, Instant now);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findByTokenHashWithLock(String tokenHash);

    int markAllUnusedAsUsed(UUID userId, UUID companyId, Instant usedAt);
}
