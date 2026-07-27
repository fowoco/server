package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.error.AuthErrorCode;
import com.fowoco.server.auth.application.error.InvalidRefreshTokenException;
import com.fowoco.server.auth.application.port.AccessTokenIssuer;
import com.fowoco.server.auth.application.port.AuthAuditPort;
import com.fowoco.server.auth.application.port.PasswordVerifier;
import com.fowoco.server.auth.application.port.RefreshTokenGenerator;
import com.fowoco.server.auth.application.port.RefreshTokenHashPort;
import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.RefreshToken;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.company.application.CompanyAuthenticationReader;
import com.fowoco.server.company.application.CompanyAuthenticationSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final CompanyAuthenticationReader companyAuthenticationReader;
    private final PasswordVerifier passwordVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final RefreshTokenRotationTransaction refreshTokenRotationTransaction;
    private final RefreshTokenLogoutTransaction refreshTokenLogoutTransaction;
    private final AuthAuditPort authAuditPort;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public AuthService(
            UserAccountRepository userAccountRepository,
            CompanyAuthenticationReader companyAuthenticationReader,
            PasswordVerifier passwordVerifier,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHashPort refreshTokenHashPort,
            RefreshTokenRotationTransaction refreshTokenRotationTransaction,
            RefreshTokenLogoutTransaction refreshTokenLogoutTransaction,
            AuthAuditPort authAuditPort,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.companyAuthenticationReader = companyAuthenticationReader;
        this.passwordVerifier = passwordVerifier;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenHashPort = refreshTokenHashPort;
        this.refreshTokenRotationTransaction = refreshTokenRotationTransaction;
        this.refreshTokenLogoutTransaction = refreshTokenLogoutTransaction;
        this.authAuditPort = authAuditPort;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        String normalizedEmail = UserAccount.normalizeEmail(command.email());
        Optional<UserAccount> userAccountCandidate = userAccountRepository.findByNormalizedEmail(normalizedEmail);

        if (userAccountCandidate.isEmpty()) {
            passwordVerifier.performDummyCheck(command.password());
            throw invalidCredentialsWithAudit();
        }

        UserAccount userAccount = userAccountCandidate.orElseThrow();
        boolean passwordMatches = passwordVerifier.matches(command.password(), userAccount.passwordHash());
        if (!passwordMatches || !userAccount.canLogin()) {
            throw invalidCredentialsWithAudit();
        }

        CompanyAuthenticationSnapshot company = companyAuthenticationReader
                .findByCompanyId(userAccount.companyId())
                .filter(CompanyAuthenticationSnapshot::authenticationAllowed)
                .orElseThrow(this::invalidCredentialsWithAudit);

        Instant issuedAt = clock.instant();
        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(userAccount, issuedAt);
        RefreshTokenGenerator.GeneratedRefreshToken generatedRefreshToken = refreshTokenGenerator.generate(issuedAt);

        RefreshToken refreshToken = RefreshToken.issue(
                uuidGenerator.generate(),
                userAccount.userId(),
                userAccount.companyId(),
                uuidGenerator.generate(),
                generatedRefreshToken.tokenHash(),
                generatedRefreshToken.expiresAt(),
                issuedAt
        );
        refreshTokenRepository.insert(refreshToken);
        authAuditPort.record(AuthAuditEvent.account(
                AuthAuditEvent.Action.LOGIN_SUCCEEDED,
                userAccount.userId(),
                userAccount.companyId(),
                issuedAt
        ));

        return new LoginResult(
                userAccount.userId(),
                userAccount.companyId(),
                company.companyName(),
                userAccount.displayName(),
                userAccount.role(),
                accessToken.value(),
                accessToken.expiresAt(),
                accessToken.expiresInSeconds(),
                generatedRefreshToken.rawValue(),
                generatedRefreshToken.expiresAt()
        );
    }

    public RefreshResult refresh(String rawRefreshToken) {
        if (!RefreshTokenFormat.isValidRawValue(rawRefreshToken)) {
            authAuditPort.record(AuthAuditEvent.anonymous(
                    AuthAuditEvent.Action.REFRESH_REJECTED,
                    clock.instant()
            ));
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = refreshTokenHashPort.hash(rawRefreshToken);
        RefreshOutcome outcome = refreshTokenRotationTransaction.rotate(tokenHash);
        return outcome.result().orElseThrow(InvalidRefreshTokenException::new);
    }

    public void logout(String rawRefreshToken) {
        if (!RefreshTokenFormat.isValidRawValue(rawRefreshToken)) {
            authAuditPort.record(AuthAuditEvent.anonymous(
                    AuthAuditEvent.Action.LOGOUT_COMPLETED,
                    clock.instant()
            ));
            return;
        }

        refreshTokenLogoutTransaction.revokeIfKnown(refreshTokenHashPort.hash(rawRefreshToken));
    }

    private ApiException invalidCredentialsWithAudit() {
        authAuditPort.record(AuthAuditEvent.anonymous(
                AuthAuditEvent.Action.LOGIN_REJECTED,
                clock.instant()
        ));
        return new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
