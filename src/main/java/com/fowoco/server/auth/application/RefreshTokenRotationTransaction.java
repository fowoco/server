package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.port.AccessTokenIssuer;
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
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public RefreshTokenRotationTransaction(
            RefreshTokenRepository refreshTokenRepository,
            UserAccountRepository userAccountRepository,
            CompanyAuthenticationReader companyAuthenticationReader,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenGenerator refreshTokenGenerator,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.companyAuthenticationReader = companyAuthenticationReader;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public RefreshOutcome rotate(String tokenHash) {
        Instant now = clock.instant();
        Optional<RefreshToken> presentedTokenCandidate =
                refreshTokenRepository.findByTokenHashWithFamilyLock(tokenHash);
        if (presentedTokenCandidate.isEmpty()) {
            return RefreshOutcome.rejected(RefreshOutcome.Status.INVALID);
        }

        RefreshToken presentedToken = presentedTokenCandidate.orElseThrow();
        if (!presentedToken.isActiveAt(now)) {
            refreshTokenRepository.revokeFamily(presentedToken.tokenFamilyId(), now);
            RefreshOutcome.Status status = presentedToken.isUsed() || presentedToken.isRevoked()
                    ? RefreshOutcome.Status.REPLAY_DETECTED
                    : RefreshOutcome.Status.INVALID;
            return RefreshOutcome.rejected(status);
        }

        Optional<UserAccount> userAccountCandidate = userAccountRepository.findByUserIdAndCompanyId(
                presentedToken.userId(),
                presentedToken.companyId()
        );
        if (userAccountCandidate.isEmpty() || !userAccountCandidate.orElseThrow().canLogin()) {
            refreshTokenRepository.revokeFamily(presentedToken.tokenFamilyId(), now);
            return RefreshOutcome.rejected(RefreshOutcome.Status.SUBJECT_DISABLED);
        }

        UserAccount userAccount = userAccountCandidate.orElseThrow();
        boolean companyCanAuthenticate = companyAuthenticationReader
                .findByCompanyId(presentedToken.companyId())
                .filter(company -> company.authenticationAllowed())
                .isPresent();
        if (!companyCanAuthenticate) {
            refreshTokenRepository.revokeFamily(presentedToken.tokenFamilyId(), now);
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

        return RefreshOutcome.succeeded(new RefreshResult(
                accessToken.value(),
                accessToken.expiresAt(),
                accessToken.expiresInSeconds(),
                generatedRefreshToken.rawValue(),
                generatedRefreshToken.expiresAt()
        ));
    }
}
