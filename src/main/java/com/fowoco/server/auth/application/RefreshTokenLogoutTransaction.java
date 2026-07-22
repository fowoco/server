package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.port.AuthAuditPort;
import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.domain.RefreshToken;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenLogoutTransaction {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthAuditPort authAuditPort;
    private final Clock clock;

    public RefreshTokenLogoutTransaction(
            RefreshTokenRepository refreshTokenRepository,
            AuthAuditPort authAuditPort,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authAuditPort = authAuditPort;
        this.clock = clock;
    }

    @Transactional
    public void revokeIfKnown(String tokenHash) {
        Optional<RefreshToken> refreshTokenCandidate =
                refreshTokenRepository.findByTokenHashWithFamilyLock(tokenHash);
        Instant now = clock.instant();
        if (refreshTokenCandidate.isEmpty()) {
            authAuditPort.record(AuthAuditEvent.anonymous(
                    AuthAuditEvent.Action.LOGOUT_COMPLETED,
                    now
            ));
            return;
        }

        RefreshToken refreshToken = refreshTokenCandidate.orElseThrow();
        int revokedTokens = refreshTokenRepository.revokeFamily(refreshToken.tokenFamilyId(), now);
        if (revokedTokens > 0) {
            recordTokenFamilyEvent(AuthAuditEvent.Action.TOKEN_FAMILY_REVOKED, refreshToken, now);
        }
        recordTokenFamilyEvent(AuthAuditEvent.Action.LOGOUT_COMPLETED, refreshToken, now);
    }

    private void recordTokenFamilyEvent(
            AuthAuditEvent.Action action,
            RefreshToken refreshToken,
            Instant occurredAt
    ) {
        authAuditPort.record(AuthAuditEvent.tokenFamily(
                action,
                refreshToken.userId(),
                refreshToken.companyId(),
                refreshToken.tokenFamilyId(),
                occurredAt
        ));
    }
}
