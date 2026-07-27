package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.port.AccessTokenIssuer;
import com.fowoco.server.auth.application.port.AuthAuditPort;
import com.fowoco.server.auth.application.port.RefreshTokenGenerator;
import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.RefreshToken;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.company.application.CompanyAuthenticationReader;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenRotationTransaction {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final CompanyAuthenticationReader companyAuthenticationReader;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final AuthAuditPort authAuditPort;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public RefreshTokenRotationTransaction(
            RefreshTokenRepository refreshTokenRepository,
            UserAccountRepository userAccountRepository,
            CompanyAuthenticationReader companyAuthenticationReader,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenGenerator refreshTokenGenerator,
            AuthAuditPort authAuditPort,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.companyAuthenticationReader = companyAuthenticationReader;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.authAuditPort = authAuditPort;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public RefreshOutcome rotate(String tokenHash) {
        Optional<RefreshToken> presentedTokenCandidate =
                refreshTokenRepository.findByTokenHashWithFamilyLock(tokenHash);
        Instant now = clock.instant();
        if (presentedTokenCandidate.isEmpty()) {
            authAuditPort.record(AuthAuditEvent.anonymous(
                    AuthAuditEvent.Action.REFRESH_REJECTED,
                    now
            ));
            return RefreshOutcome.rejected(RefreshOutcome.Status.INVALID);
        }

        RefreshToken presentedToken = presentedTokenCandidate.orElseThrow();
        if (!presentedToken.isActiveAt(now)) {
            RefreshOutcome.Status status = presentedToken.isUsed() || presentedToken.isRevoked()
                    ? RefreshOutcome.Status.REPLAY_DETECTED
                    : RefreshOutcome.Status.INVALID;
            revokeFamilyAndAudit(presentedToken, now);
            if (status == RefreshOutcome.Status.REPLAY_DETECTED) {
                recordTokenFamilyEvent(
                        AuthAuditEvent.Action.REFRESH_REUSE_DETECTED,
                        presentedToken,
                        now
                );
            } else {
                recordTokenFamilyEvent(AuthAuditEvent.Action.REFRESH_REJECTED, presentedToken, now);
            }
            return RefreshOutcome.rejected(status);
        }

        Optional<UserAccount> userAccountCandidate = userAccountRepository.findByUserIdAndCompanyId(
                presentedToken.userId(),
                presentedToken.companyId()
        );
        if (userAccountCandidate.isEmpty() || !userAccountCandidate.orElseThrow().canLogin()) {
            revokeFamilyAndAudit(presentedToken, now);
            recordTokenFamilyEvent(AuthAuditEvent.Action.REFRESH_REJECTED, presentedToken, now);
            return RefreshOutcome.rejected(RefreshOutcome.Status.SUBJECT_DISABLED);
        }

        UserAccount userAccount = userAccountCandidate.orElseThrow();
        boolean companyCanAuthenticate = companyAuthenticationReader
                .findByCompanyId(presentedToken.companyId())
                .filter(company -> company.authenticationAllowed())
                .isPresent();
        if (!companyCanAuthenticate) {
            revokeFamilyAndAudit(presentedToken, now);
            recordTokenFamilyEvent(AuthAuditEvent.Action.REFRESH_REJECTED, presentedToken, now);
            return RefreshOutcome.rejected(RefreshOutcome.Status.SUBJECT_DISABLED);
        }

        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(userAccount, now);
        RefreshTokenGenerator.GeneratedRefreshToken generatedRefreshToken = refreshTokenGenerator.generate(now);
        RefreshToken replacementToken = RefreshToken.issue(
                uuidGenerator.generate(),
                presentedToken.userId(),
                presentedToken.companyId(),
                presentedToken.tokenFamilyId(),
                generatedRefreshToken.tokenHash(),
                generatedRefreshToken.expiresAt(),
                now
        );

        refreshTokenRepository.insert(replacementToken);
        refreshTokenRepository.update(presentedToken.rotateTo(replacementToken, now));
        recordTokenFamilyEvent(AuthAuditEvent.Action.REFRESH_SUCCEEDED, presentedToken, now);

        return RefreshOutcome.succeeded(new RefreshResult(
                accessToken.value(),
                accessToken.expiresAt(),
                accessToken.expiresInSeconds(),
                generatedRefreshToken.rawValue(),
                generatedRefreshToken.expiresAt()
        ));
    }

    private void revokeFamilyAndAudit(RefreshToken refreshToken, Instant now) {
        int revokedTokens = refreshTokenRepository.revokeFamily(refreshToken.tokenFamilyId(), now);
        if (revokedTokens > 0) {
            recordTokenFamilyEvent(AuthAuditEvent.Action.TOKEN_FAMILY_REVOKED, refreshToken, now);
        }
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
