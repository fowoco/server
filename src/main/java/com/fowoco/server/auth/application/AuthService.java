package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.error.AuthErrorCode;
import com.fowoco.server.auth.application.error.InvalidRefreshTokenException;
import com.fowoco.server.auth.application.port.AccessTokenIssuer;
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
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Pattern RAW_REFRESH_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final UserAccountRepository userAccountRepository;
    private final CompanyAuthenticationReader companyAuthenticationReader;
    private final PasswordVerifier passwordVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHashPort refreshTokenHashPort;
    private final RefreshTokenRotationTransaction refreshTokenRotationTransaction;
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
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        String normalizedEmail = UserAccount.normalizeEmail(command.email());
        Optional<UserAccount> userAccountCandidate = userAccountRepository.findByNormalizedEmail(normalizedEmail);

        if (userAccountCandidate.isEmpty()) {
            passwordVerifier.performDummyCheck(command.password());
            throw invalidCredentials();
        }

        UserAccount userAccount = userAccountCandidate.orElseThrow();
        boolean passwordMatches = passwordVerifier.matches(command.password(), userAccount.passwordHash());
        if (!passwordMatches || !userAccount.canLogin()) {
            throw invalidCredentials();
        }

        CompanyAuthenticationSnapshot company = companyAuthenticationReader
                .findByCompanyId(userAccount.companyId())
                .filter(CompanyAuthenticationSnapshot::authenticationAllowed)
                .orElseThrow(AuthService::invalidCredentials);

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

        return new LoginResult(
                userAccount.userId(),
                userAccount.companyId(),
                company.companyName(),
                userAccount.role(),
                accessToken.value(),
                accessToken.expiresAt(),
                accessToken.expiresInSeconds(),
                generatedRefreshToken.rawValue(),
                generatedRefreshToken.expiresAt()
        );
    }

    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || !RAW_REFRESH_TOKEN.matcher(rawRefreshToken).matches()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = refreshTokenHashPort.hash(rawRefreshToken);
        RefreshOutcome outcome = refreshTokenRotationTransaction.rotate(tokenHash);
        return outcome.result().orElseThrow(InvalidRefreshTokenException::new);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
