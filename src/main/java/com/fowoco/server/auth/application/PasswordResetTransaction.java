package com.fowoco.server.auth.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.error.AuthErrorCode;
import com.fowoco.server.auth.application.port.AuthTenantBootstrap;
import com.fowoco.server.auth.application.port.PasswordHasher;
import com.fowoco.server.auth.application.port.PasswordResetTokenGenerator;
import com.fowoco.server.auth.application.port.PasswordResetTokenHashPort;
import com.fowoco.server.auth.application.port.PasswordResetTokenRepository;
import com.fowoco.server.auth.application.port.RefreshTokenRepository;
import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.PasswordResetToken;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.infrastructure.security.PasswordResetProperties;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetTransaction {

    private final AuthTenantBootstrap authTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenGenerator tokenGenerator;
    private final PasswordResetTokenHashPort tokenHashPort;
    private final PasswordHasher passwordHasher;
    private final PasswordResetProperties properties;
    private final AuditEventRepository auditEventRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public PasswordResetTransaction(
            AuthTenantBootstrap authTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            UserAccountRepository userAccountRepository,
            PasswordResetTokenRepository tokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenGenerator tokenGenerator,
            PasswordResetTokenHashPort tokenHashPort,
            PasswordHasher passwordHasher,
            PasswordResetProperties properties,
            AuditEventRepository auditEventRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.authTenantBootstrap = authTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.tokenHashPort = tokenHashPort;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
        this.auditEventRepository = auditEventRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public Optional<PasswordResetDispatch> issue(String email, String requestId, String traceId) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        Optional<UUID> companyId = authTenantBootstrap.findCompanyIdByNormalizedEmail(normalizedEmail);
        if (companyId.isEmpty()) {
            return Optional.empty();
        }
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId.orElseThrow());
        Optional<UserAccount> accountCandidate = userAccountRepository.findByNormalizedEmailWithLock(normalizedEmail);
        if (accountCandidate.isEmpty() || !accountCandidate.orElseThrow().canLogin()) {
            return Optional.empty();
        }

        UserAccount account = accountCandidate.orElseThrow();
        Instant now = clock.instant();
        if (tokenRepository.existsActiveCreatedAfter(
                account.userId(),
                now.minus(properties.cooldown()),
                now
        )) {
            return Optional.empty();
        }

        String rawToken = tokenGenerator.generate();
        PasswordResetToken token = PasswordResetToken.issue(
                uuidGenerator.generate(),
                account.companyId(),
                account.userId(),
                tokenHashPort.hash(rawToken),
                now.plus(properties.ttl()),
                now
        );
        tokenRepository.insert(token);
        appendAudit(
                account,
                AuditAction.PASSWORD_RESET_REQUESTED,
                token.passwordResetTokenId(),
                requestId,
                traceId,
                "비밀번호 재설정 link를 발급함",
                now
        );
        return Optional.of(new PasswordResetDispatch(account.email(), rawToken, token.expiresAt()));
    }

    @Transactional
    public void complete(String rawToken, String newPassword, String requestId, String traceId) {
        String tokenHash = tokenHashPort.hash(rawToken);
        UUID companyId = authTenantBootstrap.findCompanyIdByPasswordResetTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);

        Instant now = clock.instant();
        PasswordResetToken tokenCandidate = tokenRepository.findByTokenHash(tokenHash)
                .filter(candidate -> candidate.isUsableAt(now))
                .orElseThrow(this::invalidToken);
        UserAccount account = userAccountRepository.findByUserIdAndCompanyIdWithLock(
                        tokenCandidate.userId(),
                        companyId
                )
                .filter(UserAccount::canLogin)
                .orElseThrow(this::invalidToken);
        PasswordResetToken lockedToken = tokenRepository.findByTokenHashWithLock(tokenHash)
                .filter(candidate -> candidate.isUsableAt(now))
                .filter(candidate -> candidate.userId().equals(account.userId()))
                .orElseThrow(this::invalidToken);

        userAccountRepository.update(account.changePassword(passwordHasher.hash(newPassword), now));
        int invalidatedTokens = tokenRepository.markAllUnusedAsUsed(
                lockedToken.userId(),
                companyId,
                now
        );
        if (invalidatedTokens < 1) {
            throw invalidToken();
        }
        refreshTokenRepository.revokeAllByUser(account.userId(), now);
        appendAudit(
                account,
                AuditAction.PASSWORD_RESET_COMPLETED,
                account.userId(),
                requestId,
                traceId,
                "비밀번호를 재설정하고 기존 Refresh Token을 폐기함",
                now
        );
    }

    private void appendAudit(
            UserAccount account,
            AuditAction action,
            UUID targetId,
            String requestId,
            String traceId,
            String summary,
            Instant now
    ) {
        boolean verifiedAccountAction = action == AuditAction.PASSWORD_RESET_COMPLETED;
        auditEventRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                account.companyId(),
                verifiedAccountAction ? ActorType.HR_USER : ActorType.SYSTEM_RULE,
                verifiedAccountAction ? account.userId() : null,
                verifiedAccountAction ? account.role() : null,
                action,
                AuditTargetType.USER_ACCOUNT,
                targetId,
                requestId,
                traceId,
                "1.0",
                summary,
                now
        ));
    }

    private ApiException invalidToken() {
        return new ApiException(AuthErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }
}
