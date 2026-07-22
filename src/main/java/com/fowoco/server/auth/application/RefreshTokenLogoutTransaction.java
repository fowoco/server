package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenLogoutTransaction {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public RefreshTokenLogoutTransaction(
            RefreshTokenRepository refreshTokenRepository,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void revokeIfKnown(String tokenHash) {
        refreshTokenRepository.findByTokenHashWithFamilyLock(tokenHash)
                .ifPresent(refreshToken -> refreshTokenRepository.revokeFamily(
                        refreshToken.tokenFamilyId(),
                        clock.instant()
                ));
    }
}
